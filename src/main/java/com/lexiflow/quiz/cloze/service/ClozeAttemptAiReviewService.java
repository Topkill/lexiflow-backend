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
import com.lexiflow.async.domain.AsyncTask;
import com.lexiflow.async.domain.AsyncTaskStatus;
import com.lexiflow.async.domain.AsyncTaskType;
import com.lexiflow.async.service.AsyncTaskService;
import com.lexiflow.common.error.ErrorCode;
import com.lexiflow.common.exception.BizException;
import com.lexiflow.infra.redis.RedisDistributedLockService;
import com.lexiflow.infra.redis.RedisKeys;
import com.lexiflow.infra.redis.RedisLockAttempt;
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
import com.lexiflow.quiz.cloze.mq.ClozeReviewTaskPublisher;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClozeAttemptAiReviewService {

    private static final int STREAM_CHUNK_SIZE = 12;
    private static final int RECENT_REUSABLE_TASK_LIMIT = 50;
    private static final Duration REVIEW_TASK_CREATE_LOCK_TTL = Duration.ofSeconds(10);
    private static final Duration REVIEW_TASK_CREATE_LOCK_WAIT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration REVIEW_TASK_CREATE_LOCK_POLL_INTERVAL = Duration.ofMillis(50);

    private final AsyncTaskService asyncTaskService;
    private final AiGatewayService aiGatewayService;
    private final ClozeAttemptAiReviewMapper reviewMapper;
    private final ClozeAttemptMapper attemptMapper;
    private final ClozeQuizMapper quizMapper;
    private final ClozeQuizBlankMapper blankMapper;
    private final ClozeQuizService clozeQuizService;
    private final AiPromptTemplateService aiPromptTemplateService;
    private final AiPromptOutputSchemaService outputSchemaService;
    private final ObjectMapper objectMapper;
    private final RedisDistributedLockService redisDistributedLockService;
    private final ClozeReviewTaskPublisher clozeReviewTaskPublisher;
    private final Map<Long, Object> reviewLocks = new ConcurrentHashMap<>();

    public ClozeAttemptAiReviewResponse getReview(Long userId, Long attemptId) {
        ClozeAttempt attempt = getOwnedAttempt(userId, attemptId);
        ReviewPromptContext context = buildPromptContext(userId, attemptId, attempt);
        AsyncTask task = findReusableReviewTask(userId, attemptId, context.sourceHash(), false);
        ClozeAttemptAiReview review = getOwnedReview(userId, attemptId);
        if (review == null) {
            return ClozeAttemptAiReviewResponse.none(attemptId, task);
        }
        if (review.getStatus() == ClozeAttemptAiReviewStatus.DONE) {
            if (!context.sourceHash().equals(review.getSourceHash())) {
                return ClozeAttemptAiReviewResponse.none(attemptId, task);
            }
        }
        return ClozeAttemptAiReviewResponse.of(review, parseContent(review.getContentJson()), outputSchemaService.schemaNode(context.promptTemplate().outputSchemaJson()), task);
    }

    public ClozeAttemptAiReviewResponse createReviewTask(Long userId, Long attemptId, boolean regenerate) {
        ClozeAttempt attempt = getOwnedAttempt(userId, attemptId);
        ReviewPromptContext context = buildPromptContext(userId, attemptId, attempt);
        if (!regenerate) {
            ClozeAttemptAiReview existingReview = getDoneReviewIfFresh(userId, attemptId, context);
            if (existingReview != null) {
                AsyncTask task = findReusableReviewTask(userId, attemptId, context.sourceHash(), false);
                return ClozeAttemptAiReviewResponse.of(existingReview, parseContent(existingReview.getContentJson()), outputSchemaService.schemaNode(context.promptTemplate().outputSchemaJson()), task);
            }
        }

        ReviewTaskCreation creation = createOrReuseReviewTask(userId, attemptId, context.sourceHash(), regenerate);
        AsyncTask task = creation.task();
        if (!creation.created()) {
            ClozeAttemptAiReview review = getDoneReviewIfFresh(userId, attemptId, context);
            if (task.getStatus() == AsyncTaskStatus.SUCCESS && review != null) {
                return ClozeAttemptAiReviewResponse.of(review, parseContent(review.getContentJson()), outputSchemaService.schemaNode(context.promptTemplate().outputSchemaJson()), task);
            }
            return ClozeAttemptAiReviewResponse.none(attemptId, task);
        }
        try {
            clozeReviewTaskPublisher.publish(task.getId());
            return ClozeAttemptAiReviewResponse.none(attemptId, task);
        } catch (AmqpException ex) {
            asyncTaskService.markPendingFailed(task.getId(), String.valueOf(ErrorCode.ASYNC_TASK_FAILED.getCode()), "AI 评阅任务入队失败");
            throw new BizException(ErrorCode.ASYNC_TASK_FAILED, "AI 评阅任务入队失败");
        }
    }

    public void processReviewTask(Long taskId, boolean redelivered) {
        if (taskId == null) {
            return;
        }
        AsyncTask task = asyncTaskService.getTaskEntity(taskId);
        if (task == null) {
            log.warn("AI 评阅任务不存在，taskId={}", taskId);
            return;
        }
        if (task.getTaskType() != AsyncTaskType.AI_CLOZE_REVIEW || isTerminalStatus(task.getStatus())) {
            return;
        }
        ReviewTaskPayload payload = parseReviewTaskPayload(task.getRequestJson());
        if (!prepareReviewTaskForProcessing(task, redelivered)) {
            return;
        }
        try {
            ClozeAttempt attempt = getOwnedAttempt(task.getUserId(), payload.attemptId());
            ReviewPromptContext context = buildPromptContext(task.getUserId(), payload.attemptId(), attempt);
            if (!Objects.equals(context.sourceHash(), payload.sourceHash())) {
                throw new BizException(ErrorCode.BAD_REQUEST, "AI 评阅任务上下文已变化，请重新生成");
            }
            generateReviewForTask(task, attempt, context);
        } catch (BizException ex) {
            asyncTaskService.markFailed(task.getId(), String.valueOf(ex.getErrorCode().getCode()), ex.getCustomMessage());
        } catch (RuntimeException ex) {
            asyncTaskService.markFailed(task.getId(), String.valueOf(ErrorCode.AI_CALL_FAILED.getCode()), ex.getMessage());
            log.warn("AI 评阅任务执行失败，taskId={}", taskId, ex);
        }
    }

    public void streamReview(Long userId, Long attemptId, boolean regenerate, OutputStream outputStream) throws IOException {
        Object lock = reviewLocks.computeIfAbsent(attemptId, ignored -> new Object());
        synchronized (lock) {
            ClozeAttempt attempt = getOwnedAttempt(userId, attemptId);
            ReviewPromptContext context = buildPromptContext(userId, attemptId, attempt);
            ResolvedAiPromptTemplate promptTemplate = context.promptTemplate();
            AsyncTask task = null;
            boolean taskCreated = false;
            boolean taskFinished = false;
            try (OutputStreamWriter writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)) {
                if (!regenerate) {
                    ClozeAttemptAiReview existingReview = getDoneReviewIfFresh(userId, attemptId, context);
                    if (existingReview != null) {
                        AsyncTask reusableTask = findReusableReviewTask(userId, attemptId, context.sourceHash(), false);
                        ClozeAttemptAiReviewResponse cached = ClozeAttemptAiReviewResponse.of(existingReview, parseContent(existingReview.getContentJson()), outputSchemaService.schemaNode(promptTemplate.outputSchemaJson()), reusableTask);
                        writeEvent(writer, "status", buildReviewStatusPayload("DONE", "已命中缓存", reusableTask));
                        streamDisplayText(writer, cached.displayText());
                        writeEvent(writer, "done", cached);
                        taskFinished = true;
                        return;
                    }
                }

                ReviewTaskCreation creation = createOrReuseReviewTask(userId, attemptId, context.sourceHash(), regenerate);
                task = creation.task();
                taskCreated = creation.created();
                if (!taskCreated) {
                    ClozeAttemptAiReview review = getDoneReviewIfFresh(userId, attemptId, context);
                    if (review == null || task.getStatus() != AsyncTaskStatus.SUCCESS) {
                        writeEvent(writer, "status", Map.of(
                                "status", task.getStatus() == null ? "RUNNING" : task.getStatus().name(),
                                "message", task.getMessage() == null ? "正在生成 AI 评阅" : task.getMessage(),
                                "taskId", String.valueOf(task.getId()),
                                "taskStatus", task.getStatus() == null ? "RUNNING" : task.getStatus().name()
                        ));
                        taskFinished = true;
                        return;
                    }
                    ClozeAttemptAiReviewResponse response = ClozeAttemptAiReviewResponse.of(review, parseContent(review.getContentJson()), outputSchemaService.schemaNode(promptTemplate.outputSchemaJson()), task);
                    writeEvent(writer, "status", buildReviewStatusPayload("DONE", "AI 评阅已完成", task));
                    streamDisplayText(writer, response.displayText());
                    writeEvent(writer, "done", response);
                    taskFinished = true;
                    return;
                }

                asyncTaskService.markRunning(task.getId(), "正在生成 AI 评阅", 20);
                writeEvent(writer, "status", buildReviewStatusPayload("RUNNING", "正在生成 AI 评阅", task));
                ClozeAttemptAiReviewResponse response = generateReviewForTask(task, attempt, context);
                taskFinished = true;
                streamDisplayText(writer, response.displayText());
                writeEvent(writer, "done", response);
            } catch (IOException ex) {
                if (taskCreated && task != null && !taskFinished) {
                    markReviewTaskFailed(task, ex);
                }
                throw ex;
            } catch (BizException ex) {
                if (taskCreated && task != null && !taskFinished) {
                    asyncTaskService.markFailed(task.getId(), String.valueOf(ex.getErrorCode().getCode()), ex.getCustomMessage());
                }
                throw ex;
            } catch (RuntimeException ex) {
                if (taskCreated && task != null && !taskFinished) {
                    markReviewTaskFailed(task, ex);
                }
                throw ex;
            }
        }
    }

    private ClozeAttemptAiReviewResponse generateReviewForTask(AsyncTask task, ClozeAttempt attempt, ReviewPromptContext context) {
        ResolvedAiPromptTemplate promptTemplate = context.promptTemplate();
        ClozeAttemptAiReview review = upsertRunningReview(task.getUserId(), attempt, context.sourceHash());
        try {
            AiPrompt prompt = new AiPrompt(
                    promptTemplate.systemPrompt(),
                    buildManagedPrompt(promptTemplate, context.sourceJson()),
                    context.sourceHash(),
                    promptTemplate.featureType().name(),
                    promptTemplate.templateId(),
                    promptTemplate.templateName()
            );
            AiChatCompletionResult result = aiGatewayService.generateJson(task.getUserId(), AiContentType.CLOZE_REVIEW, prompt, task.getId());
            JsonNode contentNode = normalizeReviewContent(parseJson(result.content()), context.attemptResponse(), context.blankNoMap());
            review.setContentJson(toJson(contentNode));
            review.setStatus(ClozeAttemptAiReviewStatus.DONE);
            review.setFinishedAt(LocalDateTime.now());
            review.setErrorMessage(null);
            reviewMapper.updateById(review);
            asyncTaskService.markSuccess(task.getId(), review.getId(), "AI 评阅生成完成");
            return ClozeAttemptAiReviewResponse.of(review, contentNode, outputSchemaService.schemaNode(promptTemplate.outputSchemaJson()), task);
        } catch (BizException ex) {
            String message = ex.getErrorCode() == ErrorCode.AI_PUBLIC_QUOTA_EXHAUSTED
                    ? "今日公共 AI 调用次数已用完"
                    : StringUtils.hasText(ex.getCustomMessage()) ? ex.getCustomMessage() : "AI 评阅生成失败，请稍后重试";
            markFailed(review, message);
            throw new BizException(ex.getErrorCode(), message);
        } catch (RuntimeException ex) {
            markFailed(review, ex.getMessage());
            throw ex;
        }
    }

    private ClozeAttemptAiReview getDoneReviewIfFresh(Long userId, Long attemptId, ReviewPromptContext context) {
        ClozeAttemptAiReview existingReview = getOwnedReview(userId, attemptId);
        if (existingReview == null
                || existingReview.getStatus() != ClozeAttemptAiReviewStatus.DONE
                || !StringUtils.hasText(existingReview.getContentJson())
                || !Objects.equals(context.sourceHash(), existingReview.getSourceHash())) {
            return null;
        }
        return existingReview;
    }

    private ReviewTaskCreation createOrReuseReviewTask(Long userId, Long attemptId, String sourceHash, boolean requestedRegenerate) {
        String lockKey = RedisKeys.aiClozeReviewTaskCreateLockKey(userId, attemptId, sourceHash);
        RedisLockAttempt lockAttempt = redisDistributedLockService.tryLock(lockKey, REVIEW_TASK_CREATE_LOCK_TTL);
        if (isLockHeld(lockAttempt)) {
            AsyncTask reusableTask = waitForReusableReviewTask(userId, attemptId, sourceHash, requestedRegenerate, lockKey);
            if (reusableTask != null) {
                return new ReviewTaskCreation(reusableTask, false);
            }
            lockAttempt = redisDistributedLockService.tryLock(lockKey, REVIEW_TASK_CREATE_LOCK_TTL);
            if (isLockHeld(lockAttempt)) {
                throw new BizException(ErrorCode.ASYNC_TASK_FAILED, "AI 评阅任务正在创建，请稍后重试");
            }
        }

        try {
            AsyncTask reusableTask = findReusableReviewTask(userId, attemptId, sourceHash, requestedRegenerate);
            if (reusableTask != null) {
                return new ReviewTaskCreation(reusableTask, false);
            }
            return new ReviewTaskCreation(createReviewAsyncTask(userId, attemptId, sourceHash, requestedRegenerate), true);
        } finally {
            releaseReviewLock(lockAttempt);
        }
    }

    private AsyncTask waitForReusableReviewTask(Long userId, Long attemptId, String sourceHash, boolean requestedRegenerate, String lockKey) {
        long deadline = System.nanoTime() + REVIEW_TASK_CREATE_LOCK_WAIT_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            AsyncTask reusableTask = findReusableReviewTask(userId, attemptId, sourceHash, requestedRegenerate);
            if (reusableTask != null) {
                return reusableTask;
            }
            if (!redisDistributedLockService.isLocked(lockKey)) {
                return null;
            }
            try {
                Thread.sleep(REVIEW_TASK_CREATE_LOCK_POLL_INTERVAL.toMillis());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return findReusableReviewTask(userId, attemptId, sourceHash, requestedRegenerate);
    }

    private AsyncTask findReusableReviewTask(Long userId, Long attemptId, String sourceHash, boolean requestedRegenerate) {
        List<AsyncTask> tasks = asyncTaskService.listRecentTasks(userId, AsyncTaskType.AI_CLOZE_REVIEW, RECENT_REUSABLE_TASK_LIMIT);
        if (tasks == null) {
            return null;
        }
        return tasks.stream()
                .filter(task -> isReusableReviewTask(task, attemptId, sourceHash, requestedRegenerate))
                .findFirst()
                .orElse(null);
    }

    private boolean isReusableReviewTask(AsyncTask task, Long attemptId, String sourceHash, boolean requestedRegenerate) {
        if (task == null || task.getStatus() == AsyncTaskStatus.FAILED) {
            return false;
        }
        if (task.getStatus() == AsyncTaskStatus.SUCCESS && (requestedRegenerate || task.getResultId() == null)) {
            return false;
        }
        ReviewTaskPayload payload = readReviewTaskPayloadOrNull(task.getRequestJson());
        return payload != null
                && Objects.equals(payload.attemptId(), attemptId)
                && Objects.equals(payload.sourceHash(), sourceHash);
    }

    private boolean prepareReviewTaskForProcessing(AsyncTask task, boolean redelivered) {
        if (task.getStatus() == AsyncTaskStatus.PENDING) {
            return asyncTaskService.markRunningIfPending(task.getId(), "正在生成 AI 评阅", 20);
        }
        if (task.getStatus() == AsyncTaskStatus.RUNNING && redelivered) {
            log.warn("恢复处理 RabbitMQ 重投的 AI 评阅任务，taskId={}", task.getId());
            return true;
        }
        return false;
    }

    private boolean isTerminalStatus(AsyncTaskStatus status) {
        return status == AsyncTaskStatus.SUCCESS || status == AsyncTaskStatus.FAILED;
    }

    private boolean isLockHeld(RedisLockAttempt lockAttempt) {
        return lockAttempt != null && !lockAttempt.acquired() && !lockAttempt.unavailable();
    }

    private void releaseReviewLock(RedisLockAttempt lockAttempt) {
        if (lockAttempt != null && lockAttempt.acquired()) {
            redisDistributedLockService.release(lockAttempt.lock());
        }
    }

    private ReviewTaskPayload parseReviewTaskPayload(String requestJson) {
        ReviewTaskPayload payload = readReviewTaskPayloadOrNull(requestJson);
        if (payload == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "AI 评阅任务参数格式错误");
        }
        return payload;
    }

    private ReviewTaskPayload readReviewTaskPayloadOrNull(String requestJson) {
        if (!StringUtils.hasText(requestJson)) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(requestJson);
            Long attemptId = readLong(root.path("attemptId"));
            String sourceHash = root.path("sourceHash").asText("");
            boolean regenerate = root.path("regenerate").asBoolean(false);
            if (attemptId == null || !StringUtils.hasText(sourceHash)) {
                return null;
            }
            return new ReviewTaskPayload(attemptId, sourceHash, regenerate);
        } catch (Exception ex) {
            return null;
        }
    }

    private Long readLong(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.asLong();
        }
        String text = node.asText("");
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return Long.parseLong(text.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private void markReviewTaskFailed(AsyncTask task, Exception ex) {
        if (ex instanceof BizException bizException) {
            asyncTaskService.markFailed(
                    task.getId(),
                    String.valueOf(bizException.getErrorCode().getCode()),
                    bizException.getCustomMessage()
            );
            return;
        }
        asyncTaskService.markFailed(
                task.getId(),
                String.valueOf(ErrorCode.AI_CALL_FAILED.getCode()),
                ex.getMessage()
        );
    }

    private AsyncTask createReviewAsyncTask(Long userId, Long attemptId, String sourceHash, boolean regenerate) {
        String requestJson = toJson(Map.of(
                "attemptId", String.valueOf(attemptId),
                "sourceHash", sourceHash,
                "regenerate", regenerate
        ));
        return asyncTaskService.createTask(userId, AsyncTaskType.AI_CLOZE_REVIEW, requestJson);
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

    private Map<String, Object> buildReviewStatusPayload(String status, String message, AsyncTask task) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", status);
        payload.put("message", StringUtils.hasText(message) ? message : status);
        if (task != null) {
            payload.put("taskId", String.valueOf(task.getId()));
            payload.put("taskStatus", task.getStatus() == null ? status : task.getStatus().name());
            if (task.getResultId() != null) {
                payload.put("resultId", String.valueOf(task.getResultId()));
            }
        }
        return payload;
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

    private record ReviewTaskPayload(Long attemptId, String sourceHash, boolean regenerate) {
    }

    private record ReviewTaskCreation(AsyncTask task, boolean created) {
    }
}
