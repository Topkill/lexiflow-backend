package com.lexiflow.ai.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexiflow.ai.content.domain.AiContentCache;
import com.lexiflow.ai.content.domain.AiContentType;
import com.lexiflow.ai.content.dto.WordAiContentResponse;
import com.lexiflow.ai.content.mapper.AiContentCacheMapper;
import com.lexiflow.ai.core.dto.AiChatCompletionResult;
import com.lexiflow.ai.core.dto.AiPrompt;
import com.lexiflow.ai.core.service.AiGatewayService;
import com.lexiflow.ai.core.util.AiJsonUtils;
import com.lexiflow.ai.prompt.domain.AiPromptFeatureType;
import com.lexiflow.ai.prompt.service.AiPromptOutputSchemaService;
import com.lexiflow.ai.prompt.service.AiPromptTemplateService;
import com.lexiflow.ai.prompt.service.ResolvedAiPromptTemplate;
import com.lexiflow.common.error.ErrorCode;
import com.lexiflow.common.exception.BizException;
import com.lexiflow.user.domain.UserSettings;
import com.lexiflow.user.service.UserService;
import com.lexiflow.wordbook.domain.Word;
import com.lexiflow.wordbook.domain.Wordbook;
import com.lexiflow.wordbook.mapper.WordMapper;
import com.lexiflow.wordbook.service.WordbookService;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
public class WordAiContentService {

    private static final String SYSTEM_PROMPT = "你是 LexiFlow 的 AI 英语学习助手。请只输出合法 JSON，不要输出 JSON 外的 Markdown、解释性前后缀或代码块。内容面向备考大学生，中文为主，简洁、准确、适合背单词。";
    private static final String STREAM_OUTPUT_CONSTRAINT = "流式输出约束：JSON 对象必须先输出 answer 字段，其他字段继续按照输出 JSON 结构输出。";

    private final AiContentCacheMapper aiContentCacheMapper;
    private final AiGatewayService aiGatewayService;
    private final WordbookService wordbookService;
    private final WordMapper wordMapper;
    private final UserService userService;
    private final ObjectMapper objectMapper;
    private final AiPromptTemplateService aiPromptTemplateService;
    private final AiPromptOutputSchemaService outputSchemaService;
    private final TransactionTemplate transactionTemplate;

    public WordAiContentResponse generateWordContent(Long userId, Long wordbookId, Long wordId, AiContentType contentType, boolean regenerate) {
        return generateWordContent(userId, wordbookId, wordId, contentType, null, regenerate);
    }

    public WordAiContentResponse generateWordQuestion(Long userId, Long wordbookId, Long wordId, String question, boolean regenerate) {
        if (question == null || question.isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "问题不能为空");
        }
        return generateWordContent(userId, wordbookId, wordId, AiContentType.WORD_QA, question.trim(), regenerate);
    }

    public void streamWordQuestion(Long userId, Long wordbookId, Long wordId, String question, boolean regenerate, OutputStream outputStream) {
        if (question == null || question.isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "问题不能为空");
        }
        OutputStreamWriter writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
        try {
            Wordbook wordbook = wordbookService.getEnabledWordbook(wordbookId);
            Word word = getEnabledWord(wordbookId, wordId);
            UserSettings settings = userService.getOrCreateSettings(userId);
            String sourceJson = buildSourceJson(AiContentType.WORD_QA, wordbook, word, settings, question.trim());
            ResolvedAiPromptTemplate promptTemplate = aiPromptTemplateService.resolve(AiPromptFeatureType.WORD_QA, wordbookId);
            JsonNode outputSchema = outputSchemaService.schemaNode(promptTemplate.outputSchemaJson());
            String sourceHash = sha256(sourceJson + "\n#prompt:" + promptTemplate.cacheFingerprint());
            String cacheKey = buildCacheKey(AiContentType.WORD_QA, wordbookId, wordId, sourceHash);

            if (!regenerate) {
                AiContentCache cache = findUsableCache(AiContentType.WORD_QA, cacheKey);
                if (cache != null) {
                    incrementCacheHit(cache);
                    JsonNode content = parseJson(cache.getContentJson());
                    writeEvent(writer, "status", buildWordQaStatusPayload("CACHE_HIT", "已命中缓存", outputSchema));
                    streamCachedAnswer(writer, content);
                    streamWordQaFieldItems(writer, content, outputSchema);
                    writeEvent(writer, "done", WordAiContentResponse.of(true, AiContentType.WORD_QA, wordId, wordbookId, content, outputSchema));
                    return;
                }
            }

            writeEvent(writer, "status", buildWordQaStatusPayload("RUNNING", "正在生成 AI 回答", outputSchema));
            WordQaJsonStreamExtractor streamExtractor = new WordQaJsonStreamExtractor(outputSchema, objectMapper);
            AiPrompt prompt = buildPrompt(AiContentType.WORD_QA, sourceJson, sourceHash, promptTemplate, STREAM_OUTPUT_CONSTRAINT);
            AiChatCompletionResult result = generateQuestionStreamWithFallback(userId, prompt, writer, streamExtractor, outputSchema);
            JsonNode content = parseJson(result.content());
            if (!streamExtractor.hasAnswerEmitted()) {
                String answer = content.path("answer").asText("");
                if (!answer.isBlank()) {
                    writeEvent(writer, "chunk", Map.of("text", answer));
                }
            }
            upsertCache(AiContentType.WORD_QA, cacheKey, sourceHash, wordId, wordbookId, content);
            writeEvent(writer, "done", WordAiContentResponse.of(false, AiContentType.WORD_QA, wordId, wordbookId, content, outputSchema));
        } catch (Exception ex) {
            log.warn("AI 单词问答流式输出失败，wordId={}, wordbookId={}", wordId, wordbookId, ex);
            try {
                writeEvent(writer, "error", buildStreamErrorPayload(ex));
            } catch (Exception ignored) {
                // 客户端可能已经断开，无法继续写入错误事件。
            }
        } finally {
            try {
                writer.close();
            } catch (Exception ignored) {
                // 客户端可能已经断开。
            }
        }
    }

    private AiChatCompletionResult generateQuestionStreamWithFallback(
            Long userId,
            AiPrompt prompt,
            OutputStreamWriter writer,
            WordQaJsonStreamExtractor streamExtractor,
            JsonNode outputSchema
    ) throws Exception {
        try {
            return aiGatewayService.generateJsonStream(userId, AiContentType.WORD_QA, prompt, delta -> {
                WordQaStreamDelta streamDelta = streamExtractor.append(delta);
                if (!streamDelta.answer().isEmpty()) {
                    writeEvent(writer, "chunk", Map.of("text", streamDelta.answer()));
                }
                for (WordQaFieldItem item : streamDelta.items()) {
                    writeEvent(writer, "field_item", Map.of(
                            "field", item.field(),
                            "item", item.item()
                    ));
                }
            });
        } catch (BizException ex) {
            if (ex.getErrorCode() != ErrorCode.AI_CALL_FAILED) {
                throw ex;
            }
            AiChatCompletionResult fallback = aiGatewayService.generateJson(userId, AiContentType.WORD_QA, prompt);
            JsonNode content = parseJson(fallback.content());
            String answer = content.path("answer").asText("");
            if (!answer.isBlank()) {
                writeEvent(writer, "status", buildWordQaStatusPayload("FALLBACK", "流式响应为空，已切换为普通生成", outputSchema));
                streamText(writer, answer);
            }
            streamWordQaFieldItems(writer, content, outputSchema);
            return fallback;
        }
    }

    private Map<String, Object> buildStreamErrorPayload(Exception ex) {
        if (ex instanceof BizException bizException) {
            ErrorCode errorCode = bizException.getErrorCode();
            String message = errorCode == ErrorCode.AI_PUBLIC_QUOTA_EXHAUSTED
                    ? "今日公共 AI 调用次数已用完"
                    : bizException.getCustomMessage();
            return Map.of(
                    "code", errorCode.getCode(),
                    "message", message
            );
        }
        return Map.of(
                "code", ErrorCode.AI_CALL_FAILED.getCode(),
                "message", "AI 问答暂时不可用，请稍后重试"
        );
    }

    private WordAiContentResponse generateWordContent(Long userId, Long wordbookId, Long wordId, AiContentType contentType, String question, boolean regenerate) {
        Wordbook wordbook = wordbookService.getEnabledWordbook(wordbookId);
        Word word = getEnabledWord(wordbookId, wordId);
        UserSettings settings = userService.getOrCreateSettings(userId);
        String sourceJson = buildSourceJson(contentType, wordbook, word, settings, question);
        ResolvedAiPromptTemplate promptTemplate = contentType == AiContentType.WORD_QA
                ? aiPromptTemplateService.resolve(AiPromptFeatureType.WORD_QA, wordbookId)
                : null;
        String sourceHash = promptTemplate == null
                ? sha256(sourceJson)
                : sha256(sourceJson + "\n#prompt:" + promptTemplate.cacheFingerprint());
        String cacheKey = buildCacheKey(contentType, wordbookId, wordId, sourceHash);

        if (!regenerate) {
            AiContentCache cache = findUsableCache(contentType, cacheKey);
            if (cache != null) {
                incrementCacheHit(cache);
                JsonNode outputSchema = promptTemplate == null ? null : outputSchemaService.schemaNode(promptTemplate.outputSchemaJson());
                return WordAiContentResponse.of(true, contentType, wordId, wordbookId, parseJson(cache.getContentJson()), outputSchema);
            }
        }

        AiPrompt prompt = buildPrompt(contentType, sourceJson, sourceHash, promptTemplate);
        AiChatCompletionResult result = aiGatewayService.generateJson(userId, contentType, prompt);
        JsonNode content = parseJson(result.content());
        upsertCache(contentType, cacheKey, sourceHash, wordId, wordbookId, content);
        JsonNode outputSchema = promptTemplate == null ? null : outputSchemaService.schemaNode(promptTemplate.outputSchemaJson());
        return WordAiContentResponse.of(false, contentType, wordId, wordbookId, content, outputSchema);
    }

    private Word getEnabledWord(Long wordbookId, Long wordId) {
        Word word = wordMapper.selectOne(new LambdaQueryWrapper<Word>()
                .eq(Word::getId, wordId)
                .eq(Word::getWordbookId, wordbookId)
                .eq(Word::getEnabled, true)
                .last("LIMIT 1"));
        if (word == null) {
            throw new BizException(ErrorCode.WORD_NOT_FOUND);
        }
        return word;
    }

    private AiContentCache findUsableCache(AiContentType contentType, String cacheKey) {
        return aiContentCacheMapper.selectOne(new LambdaQueryWrapper<AiContentCache>()
                .eq(AiContentCache::getContentType, contentType)
                .eq(AiContentCache::getCacheKey, cacheKey)
                .and(wrapper -> wrapper.isNull(AiContentCache::getExpiresAt)
                        .or()
                        .gt(AiContentCache::getExpiresAt, LocalDateTime.now()))
                .last("LIMIT 1"));
    }

    private void incrementCacheHit(AiContentCache cache) {
        transactionTemplate.executeWithoutResult(status -> {
            cache.setHitCount((cache.getHitCount() == null ? 0 : cache.getHitCount()) + 1);
            aiContentCacheMapper.updateById(cache);
        });
    }

    private void upsertCache(AiContentType contentType, String cacheKey, String sourceHash, Long wordId, Long wordbookId, JsonNode content) {
        transactionTemplate.executeWithoutResult(status -> {
            AiContentCache cache = findCacheByKey(contentType, cacheKey);
            if (cache == null) {
                cache = new AiContentCache();
                cache.setContentType(contentType);
                cache.setCacheKey(cacheKey);
                cache.setUserId(null);
                cache.setWordId(wordId);
                cache.setWordbookId(wordbookId);
                cache.setSourceHash(sourceHash);
                cache.setHitCount(0);
                cache.setDeleted(0);
                fillCacheContent(cache, content);
                try {
                    aiContentCacheMapper.insert(cache);
                    return;
                } catch (DuplicateKeyException ignored) {
                    return;
                }
            }
            fillCacheContent(cache, content);
            aiContentCacheMapper.updateById(cache);
        });
    }

    private AiContentCache findCacheByKey(AiContentType contentType, String cacheKey) {
        return aiContentCacheMapper.selectOne(new LambdaQueryWrapper<AiContentCache>()
                .eq(AiContentCache::getContentType, contentType)
                .eq(AiContentCache::getCacheKey, cacheKey)
                .eq(AiContentCache::getDeleted, 0)
                .last("LIMIT 1"));
    }

    private void fillCacheContent(AiContentCache cache, JsonNode content) {
        cache.setContentJson(toJson(content));
        cache.setMarkdownContent(null);
        cache.setModelName(null);
        cache.setExpiresAt(null);
    }

    private AiPrompt buildPrompt(AiContentType contentType, String sourceJson, String sourceHash, ResolvedAiPromptTemplate promptTemplate) {
        return buildPrompt(contentType, sourceJson, sourceHash, promptTemplate, null);
    }

    private AiPrompt buildPrompt(AiContentType contentType, String sourceJson, String sourceHash, ResolvedAiPromptTemplate promptTemplate, String extraInstruction) {
        if (promptTemplate != null) {
            return new AiPrompt(
                    promptTemplate.systemPrompt(),
                    buildManagedPrompt(promptTemplate, sourceJson, extraInstruction),
                    sourceHash,
                    promptTemplate.featureType().name(),
                    promptTemplate.templateId(),
                    promptTemplate.templateName()
            );
        }
        String schema = switch (contentType) {
            case EXPLANATION -> "输出 JSON 字段：brief 字符串；usage 字符串数组；confusingWords 字符串数组；scenes 字符串数组。";
            case EXAMPLES -> "输出 JSON 字段：simple、medium、examStyle 三个对象；每个对象包含 sentence 英文例句和 translation 中文翻译。";
            case MNEMONIC -> "输出 JSON 字段：association 字符串；rootsAffixes 字符串；pitfalls 字符串数组。";
            case WORD_QA -> "输出 JSON 字段：answer 字符串；keyPoints 字符串数组；relatedWords 字符串数组；followUps 字符串数组。回答必须直接回应用户问题，不能编造未给出的固定知识；如果问题超出单词学习范围，请简短说明并拉回该单词。允许 answer 里的字符串适当使用 Markdown 语法。";
            default -> throw new BizException(ErrorCode.BAD_REQUEST, "不支持的 AI 单词内容类型");
        };
        String userPrompt = schema + "\n请基于以下单词上下文生成内容，避免编造不存在的固定搭配。\n" + sourceJson;
        if (extraInstruction != null && !extraInstruction.isBlank()) {
            userPrompt = userPrompt + "\n" + extraInstruction.trim();
        }
        return new AiPrompt(SYSTEM_PROMPT, userPrompt, sourceHash);
    }

    private String buildManagedPrompt(ResolvedAiPromptTemplate promptTemplate, String sourceJson, String extraInstruction) {
        StringBuilder prompt = new StringBuilder()
                .append("以下是本次请求的单词上下文 JSON：\n")
                .append(sourceJson)
                .append("\n\n")
                .append(promptTemplate.instructionPrompt());
        if (extraInstruction != null && !extraInstruction.isBlank()) {
            prompt.append("\n\n").append(extraInstruction.trim());
        }
        return prompt
                .append("\n\n")
                .append(outputSchemaService.buildOutputFormatPrompt(promptTemplate.outputSchemaJson()))
                .toString();
    }

    private String buildSourceJson(AiContentType contentType, Wordbook wordbook, Word word, UserSettings settings, String question) {
        if (contentType == AiContentType.WORD_QA) {
            return buildWordQaSourceJson(word, settings, question);
        }
        Map<String, Object> wordContext = new LinkedHashMap<>();
        wordContext.put("word", safe(word.getWord()));
        wordContext.put("normalizedWord", safe(word.getNormalizedWord()));
        wordContext.put("phonetic0", safe(word.getPhonetic0()));
        wordContext.put("phonetic1", safe(word.getPhonetic1()));
        wordContext.put("trans", safe(word.getTrans()));
        wordContext.put("sentences", safe(word.getSentences()));
        wordContext.put("phrases", safe(word.getPhrases()));
        wordContext.put("synos", safe(word.getSynos()));
        wordContext.put("relWords", safe(word.getRelWords()));
        wordContext.put("etymology", safe(word.getEtymology()));
        wordContext.put("primaryPos", safe(word.getPrimaryPos()));
        wordContext.put("primaryDefinition", safe(word.getPrimaryDefinition()));
        wordContext.put("tags", safe(word.getTags()));

        Map<String, Object> source = new LinkedHashMap<>();
        source.put("contentType", contentType.name());
        if (question != null && !question.isBlank()) {
            source.put("question", question.trim());
        }
        source.put("targetExam", settings.getTargetExam() == null ? "UNKNOWN" : settings.getTargetExam().name());
        source.put("wordbook", Map.of(
                "id", wordbook.getId(),
                "name", wordbook.getName(),
                "type", wordbook.getType().name(),
                "difficultyLevel", wordbook.getDifficultyLevel()
        ));
        source.put("wordbookScope", Map.of(
                "sequenceNo", word.getSequenceNo(),
                "difficultyLevel", word.getDifficultyLevel(),
                "examFrequency", word.getExamFrequency()
        ));
        source.put("word", wordContext);
        return toJson(source);
    }

    private String buildWordQaSourceJson(Word word, UserSettings settings, String question) {
        Map<String, Object> source = new LinkedHashMap<>();
        if (question != null && !question.isBlank()) {
            source.put("question", question.trim());
        }
        source.put("targetExam", settings.getTargetExam() == null ? "UNKNOWN" : settings.getTargetExam().name());
        source.put("word", safe(word.getWord()));
        source.put("trans", safe(word.getTrans()));
        return toJson(source);
    }

    private String buildCacheKey(AiContentType contentType, Long wordbookId, Long wordId, String sourceHash) {
        return contentType.name().toLowerCase(Locale.ROOT)
                + ":wordbook:" + wordbookId
                + ":word:" + wordId
                + ":" + sourceHash.substring(0, 24);
    }

    private void streamCachedAnswer(OutputStreamWriter writer, JsonNode content) throws Exception {
        String answer = content.path("answer").asText("");
        streamText(writer, answer);
    }

    private Map<String, Object> buildWordQaStatusPayload(String status, String message, JsonNode outputSchema) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", status);
        payload.put("message", message);
        payload.put("outputSchema", outputSchema);
        return payload;
    }

    private void streamWordQaFieldItems(OutputStreamWriter writer, JsonNode content, JsonNode outputSchema) throws Exception {
        for (String field : wordQaArrayFields(outputSchema)) {
            JsonNode items = content.path(field);
            if (!items.isArray()) {
                continue;
            }
            for (JsonNode item : items) {
                if (isDisplayableFieldItem(item)) {
                    writeEvent(writer, "field_item", Map.of("field", field, "item", item));
                }
            }
        }
    }

    private static boolean isDisplayableFieldItem(JsonNode item) {
        if (item == null || item.isNull() || item.isMissingNode()) {
            return false;
        }
        return !item.isTextual() || !item.asText("").isBlank();
    }

    private static Set<String> wordQaArrayFields(JsonNode outputSchema) {
        Set<String> fields = new LinkedHashSet<>();
        if (outputSchema == null || !outputSchema.isObject()) {
            return fields;
        }
        outputSchema.fields().forEachRemaining(entry -> {
            if (!"answer".equals(entry.getKey()) && entry.getValue().isArray()) {
                fields.add(entry.getKey());
            }
        });
        return fields;
    }

    private void streamText(OutputStreamWriter writer, String answer) throws Exception {
        int index = 0;
        while (index < answer.length()) {
            int next = Math.min(index + 12, answer.length());
            writeEvent(writer, "chunk", Map.of("text", answer.substring(index, next)));
            index = next;
        }
    }

    private void writeEvent(OutputStreamWriter writer, String event, Object data) throws Exception {
        writer.write("event: " + event + "\n");
        writer.write("data: " + toSseDataJson(data) + "\n\n");
        writer.flush();
    }

    private String toSseDataJson(Object data) {
        return toJson(data);
    }

    private JsonNode parseJson(String contentJson) {
        return AiJsonUtils.parseObject(objectMapper, contentJson);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new BizException(ErrorCode.INTERNAL_ERROR);
        }
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new BizException(ErrorCode.INTERNAL_ERROR);
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private static final class WordQaJsonStreamExtractor {

        private final JsonStringFieldExtractor answerExtractor = new JsonStringFieldExtractor("answer");
        private final List<JsonArrayFieldExtractor> itemExtractors;

        WordQaJsonStreamExtractor(JsonNode outputSchema, ObjectMapper objectMapper) {
            this.itemExtractors = wordQaArrayFields(outputSchema).stream()
                    .map(field -> new JsonArrayFieldExtractor(field, objectMapper))
                    .toList();
        }

        WordQaStreamDelta append(String delta) {
            String answer = answerExtractor.append(delta);
            List<WordQaFieldItem> items = new ArrayList<>();
            for (JsonArrayFieldExtractor extractor : itemExtractors) {
                for (JsonNode item : extractor.append(delta)) {
                    items.add(new WordQaFieldItem(extractor.field(), item));
                }
            }
            return new WordQaStreamDelta(answer, items);
        }

        boolean hasAnswerEmitted() {
            return answerExtractor.hasEmitted();
        }
    }

    private static final class JsonStringFieldExtractor {

        private final String key;
        private final String quotedKey;
        private final StringBuilder buffer = new StringBuilder();
        private int state = 0;
        private int index = 0;
        private boolean emitted = false;

        private JsonStringFieldExtractor(String key) {
            this.key = key;
            this.quotedKey = "\"" + key + "\"";
        }

        String append(String delta) {
            if (delta == null || delta.isEmpty() || state == 4) {
                return "";
            }
            buffer.append(delta);
            StringBuilder output = new StringBuilder();
            while (index < buffer.length() && state != 4) {
                int prevIndex = index;
                switch (state) {
                    case 0 -> findKey();
                    case 1 -> seekColon();
                    case 2 -> seekOpeningQuote();
                    case 3 -> readString(output);
                    default -> state = 4;
                }
                // readString 遇到跨 delta 的转义字符时会把 index 回退到反斜杠等待下一段内容。
                // 如果这里不检查本轮是否推进过 index，answer 里的 \n 在分片边界处可能让循环反复读取同一位置，导致死循环
                // 最终表现为后端流式抽取卡死，前端只能看到换行前的半截回答。
                //谁能想到这里的死循环会导致前端sse截断中止在\n\n呢?（说是中止在\n\n有点误导，其实是中止在第一个\n，ai模型流式输出时，第一个\n分成\和n两块了，第二个\n是完整输出没有分成两块，第一个\n分成两块，导致死循环，正好前端表现为遇到\n\n截断（同样有些误导，应该是表现为前端遇到第一个\n截断，和sse停止条件一样，因此误导了排查方向）
                if (state == 0 || index >= buffer.length() || index == prevIndex) {
                    break;
                }
            }
            if (!output.isEmpty()) {
                emitted = true;
            }
            return output.toString();
        }

        boolean hasEmitted() {
            return emitted;
        }

        private void findKey() {
            int found = buffer.indexOf(quotedKey, Math.max(0, index - quotedKey.length()));
            if (found < 0) {
                index = Math.max(0, buffer.length() - quotedKey.length());
                return;
            }
            index = found + quotedKey.length();
            state = 1;
        }

        private void seekColon() {
            while (index < buffer.length()) {
                char ch = buffer.charAt(index++);
                if (Character.isWhitespace(ch)) {
                    continue;
                }
                if (ch == ':') {
                    state = 2;
                    return;
                }
            }
        }

        private void seekOpeningQuote() {
            while (index < buffer.length()) {
                char ch = buffer.charAt(index++);
                if (Character.isWhitespace(ch)) {
                    continue;
                }
                if (ch == '"') {
                    state = 3;
                    return;
                }
            }
        }

        private void readString(StringBuilder output) {
            while (index < buffer.length()) {
                char ch = buffer.charAt(index++);
                if (ch == '"') {
                    state = 4;
                    return;
                }
                if (ch == '\\') {
                    if (index >= buffer.length()) {
                        index--;
                        return;
                    }
                    if (buffer.charAt(index) == 'u' && index + 4 >= buffer.length()) {
                        index--;
                        return;
                    }
                    appendEscapedChar(output, buffer, index);
                    index = advanceEscapedIndex(buffer, index);
                } else {
                    output.append(ch);
                }
            }
        }
    }

    private static final class JsonArrayFieldExtractor {

        private final String field;
        private final String quotedKey;
        private final ObjectMapper objectMapper;
        private final StringBuilder buffer = new StringBuilder();
        private final StringBuilder currentItem = new StringBuilder();
        private int state = 0;
        private int index = 0;
        private boolean inString = false;
        private boolean escaped = false;
        private int nestedDepth = 0;
        private boolean topLevelString = false;

        private JsonArrayFieldExtractor(String field, ObjectMapper objectMapper) {
            this.field = field;
            this.objectMapper = objectMapper;
            this.quotedKey = "\"" + field + "\"";
        }

        String field() {
            return field;
        }

        List<JsonNode> append(String delta) {
            if (delta == null || delta.isEmpty() || state == 5) {
                return List.of();
            }
            buffer.append(delta);
            List<JsonNode> items = new ArrayList<>();
            // readString 遇到跨 delta 的转义字符时会把 index 回退到反斜杠等待下一段内容。
            // 如果这里不检查本轮是否推进过 index，answer 里的 \n 在分片边界处可能让循环反复读取同一位置，导致死循环
            // 最终表现为后端流式抽取卡死，前端只能看到换行前的半截回答。
            //谁能想到这里的死循环会导致前端sse截断中止在\n\n呢?（说是中止在\n\n有点误导，其实是中止在第一个\n，ai模型流式输出时，第一个\n分成\和n两块了，第二个\n是完整输出没有分成两块，第一个\n分成两块，导致死循环，正好前端表现为遇到\n\n截断（同样有些误导，应该是表现为前端遇到第一个\n截断，和sse停止条件一样，因此误导了排查方向）    
            while (index < buffer.length() && state != 5) {
                int prevIndex = index;
                switch (state) {
                    case 0 -> findKey();
                    case 1 -> seekColon();
                    case 2 -> seekOpeningArray();
                    case 3 -> seekArrayItem(items);
                    case 4 -> readArrayItem(items);
                    default -> state = 5;
                }
                if (state == 0 || index >= buffer.length() || index == prevIndex) {
                    break;
                }
            }
            return items;
        }

        private void findKey() {
            int found = buffer.indexOf(quotedKey, Math.max(0, index - quotedKey.length()));
            if (found < 0) {
                index = Math.max(0, buffer.length() - quotedKey.length());
                return;
            }
            index = found + quotedKey.length();
            state = 1;
        }

        private void seekColon() {
            while (index < buffer.length()) {
                char ch = buffer.charAt(index++);
                if (Character.isWhitespace(ch)) {
                    continue;
                }
                if (ch == ':') {
                    state = 2;
                    return;
                }
            }
        }

        private void seekOpeningArray() {
            while (index < buffer.length()) {
                char ch = buffer.charAt(index++);
                if (Character.isWhitespace(ch)) {
                    continue;
                }
                if (ch == '[') {
                    state = 3;
                    return;
                }
            }
        }

        private void seekArrayItem(List<JsonNode> items) {
            while (index < buffer.length()) {
                char ch = buffer.charAt(index++);
                if (Character.isWhitespace(ch) || ch == ',') {
                    continue;
                }
                if (ch == ']') {
                    state = 5;
                    return;
                }
                resetItemState();
                index--;
                state = 4;
                readArrayItem(items);
                return;
            }
        }

        private void readArrayItem(List<JsonNode> items) {
            while (index < buffer.length()) {
                char ch = buffer.charAt(index++);
                if (!inString && nestedDepth == 0 && (ch == ',' || ch == ']')) {
                    emitCurrentItem(items);
                    state = ch == ']' ? 5 : 3;
                    return;
                }
                currentItem.append(ch);
                if (inString) {
                    if (escaped) {
                        escaped = false;
                    } else if (ch == '\\') {
                        escaped = true;
                    } else if (ch == '"') {
                        inString = false;
                        if (topLevelString && nestedDepth == 0) {
                            emitCurrentItem(items);
                            state = 3;
                            return;
                        }
                    }
                    continue;
                }
                if (ch == '"') {
                    inString = true;
                    topLevelString = currentItem.toString().trim().equals("\"");
                } else if (ch == '{' || ch == '[') {
                    nestedDepth++;
                } else if (ch == '}' || ch == ']') {
                    if (nestedDepth > 0) {
                        nestedDepth--;
                    }
                    if (nestedDepth == 0) {
                        emitCurrentItem(items);
                        state = 3;
                        return;
                    }
                }
            }
        }

        private void resetItemState() {
            currentItem.setLength(0);
            inString = false;
            escaped = false;
            nestedDepth = 0;
            topLevelString = false;
        }

        private void emitCurrentItem(List<JsonNode> items) {
            String raw = currentItem.toString().trim();
            resetItemState();
            if (raw.isBlank()) {
                return;
            }
            try {
                JsonNode item = objectMapper.readTree(raw);
                if (isDisplayableFieldItem(item)) {
                    items.add(item);
                }
            } catch (Exception ignored) {
                // 未完成或不合法的数组项不做增量展示，最终 done payload 仍会展示完整内容。
            }
        }
    }

    private static void appendEscapedChar(StringBuilder output, StringBuilder buffer, int escapeIndex) {
        char escaped = buffer.charAt(escapeIndex);
        if (escaped == 'u') {
            if (escapeIndex + 4 >= buffer.length()) {
                return;
            }
            String hex = buffer.substring(escapeIndex + 1, escapeIndex + 5);
            try {
                output.append((char) Integer.parseInt(hex, 16));
            } catch (NumberFormatException ex) {
                output.append("\\u").append(hex);
            }
            return;
        }
        output.append(decodeEscape(escaped));
    }

    private static int advanceEscapedIndex(StringBuilder buffer, int escapeIndex) {
        if (buffer.charAt(escapeIndex) == 'u' && escapeIndex + 4 < buffer.length()) {
            return escapeIndex + 5;
        }
        return escapeIndex + 1;
    }

    private static char decodeEscape(char escaped) {
        return switch (escaped) {
            case '"' -> '"';
            case '\\' -> '\\';
            case '/' -> '/';
            case 'b' -> '\b';
            case 'f' -> '\f';
            case 'n' -> '\n';
            case 'r' -> '\r';
            case 't' -> '\t';
            default -> escaped;
        };
    }

    private record WordQaStreamDelta(String answer, List<WordQaFieldItem> items) {
    }

    private record WordQaFieldItem(String field, JsonNode item) {
    }
}
