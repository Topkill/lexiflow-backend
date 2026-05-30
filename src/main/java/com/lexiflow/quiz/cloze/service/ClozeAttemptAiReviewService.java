package com.lexiflow.quiz.cloze.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexiflow.ai.content.domain.AiContentType;
import com.lexiflow.ai.core.dto.AiChatCompletionResult;
import com.lexiflow.ai.core.dto.AiPrompt;
import com.lexiflow.ai.core.service.AiGatewayService;
import com.lexiflow.ai.prompt.domain.AiPromptFeatureType;
import com.lexiflow.ai.prompt.service.AiPromptOutputSchemaService;
import com.lexiflow.ai.prompt.service.AiPromptTemplateService;
import com.lexiflow.ai.prompt.service.ResolvedAiPromptTemplate;
import com.lexiflow.common.error.ErrorCode;
import com.lexiflow.common.exception.BizException;
import com.lexiflow.quiz.cloze.domain.ClozeAttempt;
import com.lexiflow.quiz.cloze.domain.ClozeAttemptAiReview;
import com.lexiflow.quiz.cloze.domain.ClozeAttemptAiReviewStatus;
import com.lexiflow.quiz.cloze.domain.ClozeQuiz;
import com.lexiflow.quiz.cloze.domain.ClozeQuizBlank;
import com.lexiflow.quiz.cloze.dto.ClozeAttemptAiReviewBlankReviewResponse;
import com.lexiflow.quiz.cloze.dto.ClozeAttemptAiReviewContentResponse;
import com.lexiflow.quiz.cloze.dto.ClozeAttemptAiReviewDisplayFormatter;
import com.lexiflow.quiz.cloze.dto.ClozeAttemptAiReviewResponse;
import com.lexiflow.quiz.cloze.dto.ClozeAttemptAiReviewWeaknessResponse;
import com.lexiflow.quiz.cloze.dto.ClozeAttemptResponse;
import com.lexiflow.quiz.cloze.mapper.ClozeAttemptAiReviewMapper;
import com.lexiflow.quiz.cloze.mapper.ClozeAttemptMapper;
import com.lexiflow.quiz.cloze.mapper.ClozeQuizBlankMapper;
import com.lexiflow.quiz.cloze.mapper.ClozeQuizMapper;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ClozeAttemptAiReviewService {

    private static final int STREAM_CHUNK_SIZE = 12;

    private final AiGatewayService aiGatewayService;
    private final ClozeAttemptAiReviewMapper reviewMapper;
    private final ClozeAttemptMapper attemptMapper;
    private final ClozeQuizMapper quizMapper;
    private final ClozeQuizBlankMapper blankMapper;
    private final ClozeQuizService clozeQuizService;
    private final AiPromptTemplateService aiPromptTemplateService;
    private final AiPromptOutputSchemaService outputSchemaService;
    private final ObjectMapper objectMapper;
    private final Map<Long, Object> reviewLocks = new ConcurrentHashMap<>();

    public ClozeAttemptAiReviewResponse getReview(Long userId, Long attemptId) {
        ClozeAttemptAiReview review = getOwnedReview(userId, attemptId);
        if (review == null) {
            return ClozeAttemptAiReviewResponse.none(attemptId);
        }
        if (review.getStatus() == ClozeAttemptAiReviewStatus.DONE) {
            ClozeAttempt attempt = getOwnedAttempt(userId, attemptId);
            ReviewPromptContext context = buildPromptContext(userId, attemptId, attempt);
            if (!context.sourceHash().equals(review.getSourceHash())) {
                return ClozeAttemptAiReviewResponse.none(attemptId);
            }
        }
        return ClozeAttemptAiReviewResponse.of(review, parseContent(review.getContentJson()), outputSchemaService.schemaNode(buildPromptContext(userId, attemptId, getOwnedAttempt(userId, attemptId)).promptTemplate().outputSchemaJson()));
    }

    public void streamReview(Long userId, Long attemptId, boolean regenerate, OutputStream outputStream) throws IOException {
        Object lock = reviewLocks.computeIfAbsent(attemptId, ignored -> new Object());
        synchronized (lock) {
            ClozeAttempt attempt = getOwnedAttempt(userId, attemptId);
            ReviewPromptContext context = buildPromptContext(userId, attemptId, attempt);
            String sourceJson = context.sourceJson();
            String sourceHash = context.sourceHash();
            ResolvedAiPromptTemplate promptTemplate = context.promptTemplate();
            ClozeAttemptResponse attemptResponse = context.attemptResponse();
            Map<Long, Integer> blankNoMap = context.blankNoMap();

            ClozeAttemptAiReview existingReview = getOwnedReview(userId, attemptId);
            if (existingReview != null
                    && !regenerate
                    && existingReview.getStatus() == ClozeAttemptAiReviewStatus.DONE
                    && StringUtils.hasText(existingReview.getContentJson())
                    && sourceHash.equals(existingReview.getSourceHash())) {
                ClozeAttemptAiReviewResponse cached = ClozeAttemptAiReviewResponse.of(existingReview, parseContent(existingReview.getContentJson()), outputSchemaService.schemaNode(promptTemplate.outputSchemaJson()));
                try (OutputStreamWriter writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)) {
                    writeEvent(writer, "status", Map.of("status", "DONE", "message", "已命中缓存"));
                    streamDisplayText(writer, cached.displayText());
                    writeEvent(writer, "done", cached);
                }
                return;
            }

            ClozeAttemptAiReview review = upsertRunningReview(userId, attempt, sourceHash);
            try (OutputStreamWriter writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)) {
                writeEvent(writer, "status", Map.of("status", "RUNNING", "message", "正在生成 AI 评阅"));
                try {
                    AiPrompt prompt = new AiPrompt(
                            promptTemplate.systemPrompt(),
                            buildManagedPrompt(promptTemplate, sourceJson),
                            sourceHash,
                            promptTemplate.featureType().name(),
                            promptTemplate.templateId(),
                            promptTemplate.templateName()
                    );
                    AiChatCompletionResult result = aiGatewayService.generateJson(userId, AiContentType.CLOZE, prompt);
                    JsonNode contentNode = normalizeReviewContent(parseJson(result.content()), attemptResponse, blankNoMap);
                    review.setContentJson(toJson(contentNode));
                    review.setStatus(ClozeAttemptAiReviewStatus.DONE);
                    review.setFinishedAt(LocalDateTime.now());
                    review.setErrorMessage(null);
                    reviewMapper.updateById(review);

                    ClozeAttemptAiReviewResponse response = ClozeAttemptAiReviewResponse.of(review, contentNode, outputSchemaService.schemaNode(promptTemplate.outputSchemaJson()));
                    streamDisplayText(writer, response.displayText());
                    writeEvent(writer, "done", response);
                } catch (BizException ex) {
                    String message = ex.getErrorCode() == ErrorCode.AI_PUBLIC_QUOTA_EXHAUSTED
                            ? "今日公共 AI 调用次数已用完"
                            : StringUtils.hasText(ex.getCustomMessage()) ? ex.getCustomMessage() : "AI 评阅生成失败，请稍后重试";
                    markFailed(review, message);
                    writeEvent(writer, "error", Map.of(
                            "code", ex.getErrorCode().getCode(),
                            "message", message
                    ));
                } catch (Exception ex) {
                    markFailed(review, ex.getMessage());
                    writeEvent(writer, "error", Map.of("message", "AI 评阅生成失败，请稍后重试"));
                }
            }
        }
    }

    private ClozeAttemptAiReview getOwnedReview(Long userId, Long attemptId) {
        return reviewMapper.selectOne(new LambdaQueryWrapper<ClozeAttemptAiReview>()
                .eq(ClozeAttemptAiReview::getAttemptId, attemptId)
                .eq(ClozeAttemptAiReview::getUserId, userId)
                .last("LIMIT 1"));
    }

    private ClozeAttempt getOwnedAttempt(Long userId, Long attemptId) {
        ClozeAttempt attempt = attemptMapper.selectOne(new LambdaQueryWrapper<ClozeAttempt>()
                .eq(ClozeAttempt::getId, attemptId)
                .eq(ClozeAttempt::getUserId, userId)
                .last("LIMIT 1"));
        if (attempt == null) {
            throw new BizException(ErrorCode.CLOZE_QUIZ_NOT_FOUND);
        }
        return attempt;
    }

    private ClozeQuiz getOwnedQuiz(Long userId, Long quizId) {
        ClozeQuiz quiz = quizMapper.selectOne(new LambdaQueryWrapper<ClozeQuiz>()
                .eq(ClozeQuiz::getId, quizId)
                .eq(ClozeQuiz::getUserId, userId)
                .last("LIMIT 1"));
        if (quiz == null) {
            throw new BizException(ErrorCode.CLOZE_QUIZ_NOT_FOUND);
        }
        return quiz;
    }

    private List<ClozeQuizBlank> loadBlanks(Long quizId) {
        return blankMapper.selectList(new LambdaQueryWrapper<ClozeQuizBlank>()
                .eq(ClozeQuizBlank::getQuizId, quizId)
                .orderByAsc(ClozeQuizBlank::getBlankNo)
                .orderByAsc(ClozeQuizBlank::getId));
    }

    private ReviewPromptContext buildPromptContext(Long userId, Long attemptId, ClozeAttempt attempt) {
        ClozeQuiz quiz = getOwnedQuiz(userId, attempt.getQuizId());
        List<ClozeQuizBlank> blanks = loadBlanks(attempt.getQuizId());
        ClozeAttemptResponse attemptResponse = clozeQuizService.getAttempt(userId, attemptId);
        Map<Long, Integer> blankNoMap = blanks.stream().collect(java.util.stream.Collectors.toMap(ClozeQuizBlank::getId, ClozeQuizBlank::getBlankNo));
        String sourceJson = toJson(buildSource(quiz, attemptResponse, blankNoMap));
        ResolvedAiPromptTemplate promptTemplate = aiPromptTemplateService.resolve(AiPromptFeatureType.CLOZE_REVIEW, quiz.getWordbookId());
        String sourceHash = sha256(sourceJson + "\n#prompt:" + promptTemplate.cacheFingerprint());
        return new ReviewPromptContext(sourceJson, sourceHash, promptTemplate, attemptResponse, blankNoMap);
    }

    @Transactional
    protected ClozeAttemptAiReview upsertRunningReview(Long userId, ClozeAttempt attempt, String sourceHash) {
        ClozeAttemptAiReview review = reviewMapper.selectOne(new LambdaQueryWrapper<ClozeAttemptAiReview>()
                .eq(ClozeAttemptAiReview::getAttemptId, attempt.getId())
                .eq(ClozeAttemptAiReview::getUserId, userId)
                .last("LIMIT 1"));
        if (review == null) {
            review = new ClozeAttemptAiReview();
            review.setAttemptId(attempt.getId());
            review.setQuizId(attempt.getQuizId());
            review.setUserId(userId);
            review.setWordbookId(attempt.getWordbookId());
            review.setDeleted(0);
        }
        review.setSourceHash(sourceHash);
        review.setContentJson(null);
        review.setStatus(ClozeAttemptAiReviewStatus.RUNNING);
        review.setStartedAt(LocalDateTime.now());
        review.setFinishedAt(null);
        review.setErrorMessage(null);
        review.setModelName(null);
        if (review.getId() == null) {
            reviewMapper.insert(review);
        } else {
            reviewMapper.updateById(review);
        }
        return review;
    }

    @Transactional
    protected void markFailed(ClozeAttemptAiReview review, String message) {
        review.setStatus(ClozeAttemptAiReviewStatus.FAILED);
        review.setErrorMessage(StringUtils.hasText(message) ? message.trim() : "AI 评阅失败");
        review.setFinishedAt(LocalDateTime.now());
        reviewMapper.updateById(review);
    }

    private Map<String, Object> buildSource(ClozeQuiz quiz, ClozeAttemptResponse attemptResponse, Map<Long, Integer> blankNoMap) {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("passage", safe(quiz.getPassage()));
        Map<String, Object> attemptSummary = new LinkedHashMap<>();
        attemptSummary.put("totalBlanks", attemptResponse.totalBlanks());
        attemptSummary.put("correctCount", attemptResponse.correctCount());
        attemptSummary.put("wrongCount", attemptResponse.wrongCount());
        attemptSummary.put("score", attemptResponse.score());
        source.put("attempt", attemptSummary);
        source.put("answers", attemptResponse.answers().stream().map(answer -> {
            Map<String, Object> item = new LinkedHashMap<>();
            Long blankId = parseLong(answer.blankId());
            item.put("blankNo", blankNoMap.get(blankId));
            item.put("correctAnswer", safe(answer.correctAnswer()));
            item.put("userAnswer", safe(answer.userAnswer()));
            item.put("correct", answer.correct());
            item.put("usedPos", safe(answer.correctAnswerPos()));
            item.put("definitionZh", safe(answer.correctDefinitionZh()));
            item.put("reasonZh", safe(answer.reasonZh()));
            if (Boolean.FALSE.equals(answer.correct())) {
                item.put("correctDefinitions", answer.correctDefinitions());
            }
            return item;
        }).toList());
        return source;
    }

    private JsonNode normalizeReviewContent(JsonNode content, ClozeAttemptResponse attemptResponse, Map<Long, Integer> blankNoMap) {
        ClozeAttemptAiReviewContentResponse parsed = ClozeAttemptAiReviewContentResponse.from(content);
        if (parsed != null) {
            return content;
        }
        return objectMapper.valueToTree(buildFallbackContent(attemptResponse, blankNoMap));
    }

    private String buildManagedPrompt(ResolvedAiPromptTemplate promptTemplate, String sourceJson) {
        return "以下是本次完形填空作答上下文 JSON：\n"
                + sourceJson
                + "\n\n"
                + promptTemplate.instructionPrompt()
                + "\n\n"
                + outputSchemaService.buildOutputFormatPrompt(promptTemplate.outputSchemaJson());
    }

    private JsonNode parseContent(String contentJson) {
        if (!StringUtils.hasText(contentJson)) {
            return null;
        }
        try {
            return objectMapper.readTree(contentJson);
        } catch (Exception ex) {
            return null;
        }
    }

    private ClozeAttemptAiReviewContentResponse buildFallbackContent(ClozeAttemptResponse attemptResponse, Map<Long, Integer> blankNoMap) {
        List<Integer> wrongBlankNos = new ArrayList<>();
        List<String> strengths = new ArrayList<>();
        if (attemptResponse.correctCount() != null && attemptResponse.totalBlanks() != null) {
            strengths.add("本次答对 " + attemptResponse.correctCount() + " / " + attemptResponse.totalBlanks() + " 个空，说明整体上下文把握还可以。");
        }
        List<ClozeAttemptAiReviewBlankReviewResponse> blankReviews = new ArrayList<>();
        for (var answer : attemptResponse.answers()) {
            if (!Boolean.FALSE.equals(answer.correct())) {
                continue;
            }
            Long blankId = parseLong(answer.blankId());
            Integer blankNo = blankNoMap.get(blankId);
            if (blankNo != null) {
                wrongBlankNos.add(blankNo);
            }
            String comment = StringUtils.hasText(answer.reasonZh())
                    ? answer.reasonZh()
                    : "这里更适合使用 " + answer.correctAnswer() + "。";
            String tip = StringUtils.hasText(answer.correctDefinitionZh())
                    ? "重点复盘这个词在当前词性下的中文释义。"
                    : "回到例句里重新判断语境。";
            blankReviews.add(ClozeAttemptAiReviewBlankReviewResponse.of(blankNo, comment, tip));
        }
        List<String> mistakeTags = wrongBlankNos.isEmpty()
                ? List.of("上下文判断")
                : List.of("上下文判断", "词义辨析");
        List<String> suggestions = List.of(
                "先看空格前后 3 到 5 个词，再判断词性和语义。",
                "遇到近义选项时，优先比较固定搭配和上下文逻辑。",
                "错题空格回到原句复盘，不要只记答案。"
        );
        return new ClozeAttemptAiReviewContentResponse(
                "本次作答已经能抓住部分上下文，但在个别空格的词义和搭配上还可以更稳一点。",
                mistakeTags,
                strengths,
                List.of(ClozeAttemptAiReviewWeaknessResponse.of("词义辨析", wrongBlankNos, "个别空格的中文释义判断还不够稳定。")),
                suggestions,
                blankReviews,
                ""
        );
    }

    private void streamDisplayText(OutputStreamWriter writer, String displayText) throws IOException {
        String text = StringUtils.hasText(displayText) ? displayText : "暂无可展示的评阅内容。";
        for (int index = 0; index < text.length(); index += STREAM_CHUNK_SIZE) {
            int end = Math.min(text.length(), index + STREAM_CHUNK_SIZE);
            writeEvent(writer, "chunk", Map.of("text", text.substring(index, end)));
            sleepQuietly(12L);
        }
    }

    private void writeEvent(OutputStreamWriter writer, String event, Object data) throws IOException {
        writer.write("event: ");
        writer.write(event);
        writer.write('\n');
        writer.write("data: ");
        writer.write(toSseDataJson(data));
        writer.write("\n\n");
        writer.flush();
    }

    private String toSseDataJson(Object data) {
        return toJson(data);
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private JsonNode parseJson(String content) {
        try {
            return objectMapper.readTree(content);
        } catch (Exception ex) {
            throw new BizException(ErrorCode.AI_CALL_FAILED, "AI 评阅返回内容不是合法 JSON");
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new BizException(ErrorCode.INTERNAL_ERROR);
        }
    }

    private Long parseLong(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : digest) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (Exception ex) {
            throw new BizException(ErrorCode.INTERNAL_ERROR);
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private record ReviewPromptContext(
            String sourceJson,
            String sourceHash,
            ResolvedAiPromptTemplate promptTemplate,
            ClozeAttemptResponse attemptResponse,
            Map<Long, Integer> blankNoMap
    ) {
    }
}
