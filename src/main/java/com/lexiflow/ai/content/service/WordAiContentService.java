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
import com.lexiflow.common.error.ErrorCode;
import com.lexiflow.common.exception.BizException;
import com.lexiflow.user.domain.UserSettings;
import com.lexiflow.user.service.UserService;
import com.lexiflow.wordbook.domain.Word;
import com.lexiflow.wordbook.domain.Wordbook;
import com.lexiflow.wordbook.domain.WordbookWord;
import com.lexiflow.wordbook.mapper.WordMapper;
import com.lexiflow.wordbook.mapper.WordbookWordMapper;
import com.lexiflow.wordbook.service.WordbookService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WordAiContentService {

    private static final String SYSTEM_PROMPT = "你是 LexiFlow 的 AI 英语学习助手。请只输出合法 JSON，不要输出 Markdown、解释性前后缀或代码块。内容面向备考大学生，中文为主，简洁、准确、适合背单词。";

    private final AiContentCacheMapper aiContentCacheMapper;
    private final AiGatewayService aiGatewayService;
    private final WordbookService wordbookService;
    private final WordbookWordMapper wordbookWordMapper;
    private final WordMapper wordMapper;
    private final UserService userService;
    private final ObjectMapper objectMapper;

    @Transactional
    public WordAiContentResponse generateWordContent(Long userId, Long wordbookId, Long wordId, AiContentType contentType, boolean regenerate) {
        Wordbook wordbook = wordbookService.getEnabledWordbook(wordbookId);
        WordbookWord relation = getEnabledRelation(wordbookId, wordId);
        Word word = getWord(wordId);
        UserSettings settings = userService.getOrCreateSettings(userId);
        String sourceJson = buildSourceJson(contentType, wordbook, relation, word, settings);
        String sourceHash = sha256(sourceJson);
        String cacheKey = buildCacheKey(contentType, wordbookId, wordId, sourceHash);

        if (!regenerate) {
            AiContentCache cache = findUsableCache(contentType, cacheKey);
            if (cache != null) {
                cache.setHitCount((cache.getHitCount() == null ? 0 : cache.getHitCount()) + 1);
                aiContentCacheMapper.updateById(cache);
                return WordAiContentResponse.of(true, contentType, wordId, wordbookId, parseJson(cache.getContentJson()));
            }
        }

        AiPrompt prompt = buildPrompt(contentType, sourceJson, sourceHash);
        AiChatCompletionResult result = aiGatewayService.generateJson(userId, contentType, prompt);
        JsonNode content = parseJson(cleanJson(result.content()));
        upsertCache(contentType, cacheKey, sourceHash, wordId, wordbookId, content);
        return WordAiContentResponse.of(false, contentType, wordId, wordbookId, content);
    }

    private WordbookWord getEnabledRelation(Long wordbookId, Long wordId) {
        WordbookWord relation = wordbookWordMapper.selectOne(new LambdaQueryWrapper<WordbookWord>()
                .eq(WordbookWord::getWordbookId, wordbookId)
                .eq(WordbookWord::getWordId, wordId)
                .eq(WordbookWord::getEnabled, true)
                .last("LIMIT 1"));
        if (relation == null) {
            throw new BizException(ErrorCode.WORD_NOT_FOUND);
        }
        return relation;
    }

    private Word getWord(Long wordId) {
        Word word = wordMapper.selectById(wordId);
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

    private void upsertCache(AiContentType contentType, String cacheKey, String sourceHash, Long wordId, Long wordbookId, JsonNode content) {
        AiContentCache cache = aiContentCacheMapper.selectOne(new LambdaQueryWrapper<AiContentCache>()
                .eq(AiContentCache::getContentType, contentType)
                .eq(AiContentCache::getCacheKey, cacheKey)
                .last("LIMIT 1"));
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
            cache.setVersion(0);
        }
        cache.setContentJson(toJson(content));
        cache.setMarkdownContent(null);
        cache.setModelName(null);
        cache.setExpiresAt(null);
        if (cache.getId() == null) {
            aiContentCacheMapper.insert(cache);
        } else {
            aiContentCacheMapper.updateById(cache);
        }
    }

    private AiPrompt buildPrompt(AiContentType contentType, String sourceJson, String sourceHash) {
        String schema = switch (contentType) {
            case EXPLANATION -> "输出 JSON 字段：brief 字符串；usage 字符串数组；confusingWords 字符串数组；scenes 字符串数组。";
            case EXAMPLES -> "输出 JSON 字段：simple、medium、examStyle 三个对象；每个对象包含 sentence 英文例句和 translation 中文翻译。";
            case MNEMONIC -> "输出 JSON 字段：association 字符串；rootsAffixes 字符串；pitfalls 字符串数组。";
            default -> throw new BizException(ErrorCode.BAD_REQUEST, "不支持的 AI 单词内容类型");
        };
        String userPrompt = schema + "\n请基于以下单词上下文生成内容，避免编造不存在的固定搭配。\n" + sourceJson;
        return new AiPrompt(SYSTEM_PROMPT, userPrompt, sourceHash);
    }

    private String buildSourceJson(AiContentType contentType, Wordbook wordbook, WordbookWord relation, Word word, UserSettings settings) {
        Map<String, Object> source = Map.of(
                "contentType", contentType.name(),
                "targetExam", settings.getTargetExam() == null ? "UNKNOWN" : settings.getTargetExam().name(),
                "wordbook", Map.of(
                        "id", wordbook.getId(),
                        "name", wordbook.getName(),
                        "type", wordbook.getType().name(),
                        "difficultyLevel", wordbook.getDifficultyLevel()
                ),
                "wordbookWord", Map.of(
                        "sequenceNo", relation.getSequenceNo(),
                        "difficultyLevel", relation.getDifficultyLevel(),
                        "examFrequency", relation.getExamFrequency()
                ),
                "word", Map.of(
                        "wordText", safe(word.getWordText()),
                        "displayText", safe(word.getDisplayText()),
                        "phoneticUs", safe(word.getPhoneticUs()),
                        "phoneticUk", safe(word.getPhoneticUk()),
                        "primaryPos", safe(word.getPrimaryPos()),
                        "primaryDefinition", safe(word.getPrimaryDefinition()),
                        "meanings", safe(word.getMeanings()),
                        "exampleSentence", safe(word.getExampleSentence()),
                        "exampleTranslation", safe(word.getExampleTranslation()),
                        "tags", safe(word.getTags())
                )
        );
        return toJson(source);
    }

    private String buildCacheKey(AiContentType contentType, Long wordbookId, Long wordId, String sourceHash) {
        return contentType.name().toLowerCase(Locale.ROOT)
                + ":wordbook:" + wordbookId
                + ":word:" + wordId
                + ":" + sourceHash.substring(0, 24);
    }

    private JsonNode parseJson(String contentJson) {
        try {
            JsonNode node = objectMapper.readTree(contentJson);
            if (!node.isObject()) {
                throw new BizException(ErrorCode.AI_CALL_FAILED, "AI 返回内容不是 JSON 对象");
            }
            return node;
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException(ErrorCode.AI_CALL_FAILED, "AI 返回内容解析失败");
        }
    }

    private String cleanJson(String content) {
        String text = content == null ? "" : content.trim();
        if (text.startsWith("```")) {
            int firstLineBreak = text.indexOf('\n');
            int lastFence = text.lastIndexOf("```");
            if (firstLineBreak >= 0 && lastFence > firstLineBreak) {
                text = text.substring(firstLineBreak + 1, lastFence).trim();
            }
        }
        int firstBrace = text.indexOf('{');
        int lastBrace = text.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            return text.substring(firstBrace, lastBrace + 1);
        }
        return text;
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
}