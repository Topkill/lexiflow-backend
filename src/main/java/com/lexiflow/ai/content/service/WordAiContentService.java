package com.lexiflow.ai.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexiflow.ai.content.domain.AiContentType;
import com.lexiflow.ai.content.domain.WordAiQa;
import com.lexiflow.ai.content.dto.WordAiContentResponse;
import com.lexiflow.ai.content.mapper.WordAiQaMapper;
import com.lexiflow.ai.content.mq.WordQaTaskPublisher;
import com.lexiflow.ai.core.dto.AiChatCompletionResult;
import com.lexiflow.ai.core.dto.AiPrompt;
import com.lexiflow.ai.core.service.AiGatewayService;
import com.lexiflow.ai.core.util.AiJsonUtils;
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
import com.lexiflow.infra.redis.RedisAiHitCountBuffer;
import com.lexiflow.infra.redis.RedisDistributedLockService;
import com.lexiflow.infra.redis.RedisKeys;
import com.lexiflow.infra.redis.RedisLockAttempt;
import com.lexiflow.user.domain.UserSettings;
import com.lexiflow.user.service.UserService;
import com.lexiflow.wordbook.domain.Word;
import com.lexiflow.wordbook.domain.Wordbook;
import com.lexiflow.wordbook.mapper.WordMapper;
import com.lexiflow.wordbook.service.WordbookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class WordAiContentService {

    private static final String SYSTEM_PROMPT = "你是 LexiFlow 的 AI 英语学习助手。请只输出合法 JSON，不要输出 JSON 外的 Markdown、解释性前后缀或代码块。内容面向备考大学生，中文为主，简洁、准确、适合背单词。";
    private static final String STREAM_OUTPUT_CONSTRAINT = "流式输出约束：JSON 对象必须先输出 answer 字段，其他字段继续按照输出 JSON 结构输出。";
    private static final int WORD_QA_CACHE_LOCK_STRIPES = 64;
    private static final Duration AI_CACHE_LOCK_TTL = Duration.ofSeconds(130);
    private static final Duration AI_CACHE_LOCK_WAIT_TIMEOUT = Duration.ofSeconds(115);
    private static final Duration AI_CACHE_LOCK_POLL_INTERVAL = Duration.ofMillis(500);
    private static final int RECENT_REUSABLE_TASK_LIMIT = 50;
    private static final Duration WORD_QA_TASK_CREATE_LOCK_TTL = Duration.ofSeconds(10);
    private static final Duration WORD_QA_TASK_CREATE_LOCK_WAIT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration WORD_QA_TASK_CREATE_LOCK_POLL_INTERVAL = Duration.ofMillis(50);

    private final WordAiQaMapper wordAiQaMapper;
    private final AsyncTaskService asyncTaskService;
    private final AiGatewayService aiGatewayService;
    private final WordbookService wordbookService;
    private final WordMapper wordMapper;
    private final UserService userService;
    private final ObjectMapper objectMapper;
    private final AiPromptTemplateService aiPromptTemplateService;
    private final AiPromptOutputSchemaService outputSchemaService;
    private final TransactionTemplate transactionTemplate;
    private final RedisAiHitCountBuffer redisAiHitCountBuffer;
    private final RedisDistributedLockService redisDistributedLockService;
    private final WordQaTaskPublisher wordQaTaskPublisher;


    /**
     * 分段锁数组，用于减少缓存访问时的并发竞争
     */
    private final Object[] wordQaCacheLocks = createWordQaCacheLocks();

    /**
     * 创建单词问答缓存锁数组
     * <p>
     * 该方法初始化一个对象数组，用于实现缓存的分段锁机制。
     * 通过多个锁对象来减少并发竞争，提高缓存访问的并发性能。
     * </p>
     *
     * @return Object[] 分段锁对象数组
     */
    private static Object[] createWordQaCacheLocks() {
        Object[] locks = new Object[WORD_QA_CACHE_LOCK_STRIPES];
        for (int i = 0; i < locks.length; i++) {
            locks[i] = new Object();
        }
        return locks;
    }

    /**
     * 生成单词AI问题回答
     * <p>
     * 该方法处理用户对单词的提问，支持同步生成和缓存复用。
     * 如果问题是空的或空白，则抛出业务异常。
     * </p>
     *
     * @param userId 用户ID
     * @param wordbookId 单词本ID
     * @param wordId 单词ID
     * @param question 用户提出的问题
     * @param regenerate 是否强制重新生成，忽略缓存
     * @return WordAiContentResponse AI生成的问题回答响应对象
     * @throws BizException 当问题为空时抛出异常
     */
    public WordAiContentResponse generateWordQuestion(Long userId, Long wordbookId, Long wordId, String question, boolean regenerate) {
        if (question == null || question.isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "问题不能为空");
        }
        return generateWordQaContent(userId, wordbookId, wordId, question.trim(), regenerate);
    }

    /**
     * 查询单词问题的状态
     * <p>
     * 该方法检查指定问题的AI回答是否已经生成完成。
     * 首先查找可复用的异步任务，如果存在则返回任务状态。
     * 如果没有正在进行的任务，则检查缓存中是否有已完成的回答。
     * </p>
     *
     * @param userId 用户ID
     * @param wordbookId 单词本ID
     * @param wordId 单词ID
     * @param question 用户提出的问题
     * @return WordAiContentResponse 包含任务状态或缓存结果的响应对象
     * @throws BizException 当问题为空时抛出异常
     */
    public WordAiContentResponse getWordQuestionState(Long userId, Long wordbookId, Long wordId, String question) {
        if (question == null || question.isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "问题不能为空");
        }
        WordQaContext context = buildWordQaContext(userId, wordbookId, wordId, question.trim());
        AsyncTask reusableTask = findReusableWordQaTask(userId, context, false);
        if (reusableTask != null) {
            return buildWordQaTaskResponse(reusableTask, context);
        }
        WordAiQa cached = findActiveWordQa(context.cacheKey());
        if (cached == null) {
            return WordAiContentResponse.of(false, AiContentType.WORD_QA, null, wordId, wordbookId, null, context.outputSchema(), null, "NONE", null);
        }
        incrementWordQaHit(cached);
        return WordAiContentResponse.of(true, AiContentType.WORD_QA, cached.getId(), wordId, wordbookId, parseJson(cached.getContentJson()), context.outputSchema(), null, "SUCCESS", "AI 问答命中缓存");
    }

    /**
     * 创建单词问题异步任务
     * <p>
     * 该方法为用户的问题创建一个新的异步AI回答生成任务，或者复用已有的任务。
     * 如果任务创建成功，会将任务发布到消息队列进行异步处理。
     * 如果消息队列入队失败，会将任务标记为失败状态。
     * </p>
     *
     * @param userId 用户ID
     * @param wordbookId 单词本ID
     * @param wordId 单词ID
     * @param question 用户提出的问题
     * @param regenerate 是否强制重新生成，忽略现有结果
     * @return WordAiContentResponse 包含任务信息的响应对象
     * @throws BizException 当问题为空或任务入队失败时抛出异常
     */
    public WordAiContentResponse createWordQuestionTask(Long userId, Long wordbookId, Long wordId, String question, boolean regenerate) {
        if (question == null || question.isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "问题不能为空");
        }
        WordQaContext context = buildWordQaContext(userId, wordbookId, wordId, question.trim());
        WordQaTaskCreation creation = createOrReuseWordQaTask(userId, context, regenerate);
        AsyncTask task = creation.task();
        if (!creation.created()) {
            return buildWordQaTaskResponse(task, context);
        }
        try {
            wordQaTaskPublisher.publish(task.getId());
            return buildWordQaTaskResponse(task, context);
        } catch (AmqpException ex) {
            asyncTaskService.markPendingFailed(task.getId(), String.valueOf(ErrorCode.ASYNC_TASK_FAILED.getCode()), "AI 问答任务入队失败");
            throw new BizException(ErrorCode.ASYNC_TASK_FAILED, "AI 问答任务入队失败");
        }
    }

    /**
     * 处理单词问答异步任务
     * <p>
     * 该方法由异步任务消费者调用，负责执行实际的AI问答生成逻辑。
     * 会验证任务的有效性、上下文的一致性，并调用AI服务生成回答。
     * 生成完成后会保存结果到数据库并更新任务状态。
     * </p>
     *
     * @param taskId 异步任务ID
     * @param redelivered 是否为消息队列重投的任务
     */
    public void processWordQaTask(Long taskId, boolean redelivered) {
        if (taskId == null) {
            return;
        }
        AsyncTask task = asyncTaskService.getTaskEntity(taskId);
        if (task == null) {
            log.warn("AI 问答任务不存在，taskId={}", taskId);
            return;
        }
        if (task.getTaskType() != AsyncTaskType.AI_WORD_QA || isTerminalStatus(task.getStatus())) {
            return;
        }
        WordQaTaskPayload payload = parseWordQaTaskPayload(task.getRequestJson());
        if (!prepareWordQaTaskForProcessing(task, redelivered)) {
            return;
        }
        try {
            WordQaContext context = buildWordQaContext(task.getUserId(), payload.wordbookId(), payload.wordId(), payload.question());
            if (!Objects.equals(context.sourceHash(), payload.sourceHash())) {
                throw new BizException(ErrorCode.BAD_REQUEST, "AI 问答任务上下文已变化，请重新生成");
            }
            generateWordQaForTask(task, context, payload.regenerate());
        } catch (BizException ex) {
            markWordQaTaskFailed(task, ex);
        } catch (RuntimeException ex) {
            markWordQaTaskFailed(task, ex);
            log.warn("AI 问答任务执行失败，taskId={}", taskId, ex);
        }
    }

    /**
     * 流式输出单词问题回答
     * <p>
     * 该方法支持SSE（Server-Sent Events）流式输出AI生成的回答。
     * 实现了复杂的缓存和锁机制来处理并发请求：
     * 1. 首先检查是否有可复用的已完成任务或缓存结果
     * 2. 如果没有，尝试获取分布式锁来生成新的回答
     * 3. 如果其他请求正在生成相同内容，则等待其完成并复用结果
     * 4. 支持流式fallback机制，当流式响应失败时切换到普通生成
     * </p>
     *
     * @param userId 用户ID
     * @param wordbookId 单词本ID
     * @param wordId 单词ID
     * @param question 用户提出的问题
     * @param regenerate 是否强制重新生成
     * @param outputStream 用于输出SSE事件的输出流
     * @throws BizException 当问题为空或AI调用失败时抛出异常
     */
    public void streamWordQuestion(Long userId, Long wordbookId, Long wordId, String question, boolean regenerate, OutputStream outputStream) {
        if (question == null || question.isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "问题不能为空");
        }
        OutputStreamWriter writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
        AsyncTask task = null;
        boolean taskFinished = false;
        boolean taskCreated = false;
        try {
            String safeQuestion = question.trim();
            WordQaContext context = buildWordQaContext(userId, wordbookId, wordId, safeQuestion);
            WordQaTaskCreation creation = createOrReuseWordQaTask(userId, context, regenerate);
            task = creation.task();
            taskCreated = creation.created();
            if (!taskCreated) {
                streamReusableWordQaTask(writer, task, context);
                taskFinished = true;
                return;
            }
            asyncTaskService.markRunning(task.getId(), "正在生成 AI 回答", 20);

            synchronized (wordQaCacheLock(context.cacheKey())) {
                if (!regenerate) {
                    WordAiQa cached = findActiveWordQa(context.cacheKey());
                    if (cached != null) {
                        incrementWordQaHit(cached);
                        JsonNode content = parseJson(cached.getContentJson());
                        asyncTaskService.markSuccess(task.getId(), cached.getId(), "AI 问答命中缓存");
                        taskFinished = true;
                        writeEvent(writer, "status", buildWordQaStatusPayload("CACHE_HIT", "已命中缓存", context.outputSchema(), task));
                        streamCachedAnswer(writer, content);
                        streamWordQaFieldItems(writer, content, context.outputSchema());
                        writeEvent(writer, "done", WordAiContentResponse.of(true, AiContentType.WORD_QA, cached.getId(), wordId, wordbookId, content, context.outputSchema(), task.getId(), "SUCCESS", "AI 问答命中缓存"));
                        return;
                    }
                }

                RedisLockAttempt lockAttempt = tryAcquireWordQaLock(context.cacheKey(), regenerate);
                try {
                    if (isLockHeld(lockAttempt)) {
                        writeEvent(writer, "status", buildWordQaStatusPayload("RUNNING", "正在等待相同 AI 回答生成", context.outputSchema(), task));
                        WordAiQa cached = waitForWordQaCache(context.cacheKey(), lockAttempt.lock().key());
                        if (cached != null) {
                            incrementWordQaHit(cached);
                            JsonNode content = parseJson(cached.getContentJson());
                            asyncTaskService.markSuccess(task.getId(), cached.getId(), "AI 问答命中缓存");
                            taskFinished = true;
                            writeEvent(writer, "status", buildWordQaStatusPayload("CACHE_HIT", "已命中缓存", context.outputSchema(), task));
                            streamCachedAnswer(writer, content);
                            streamWordQaFieldItems(writer, content, context.outputSchema());
                            writeEvent(writer, "done", WordAiContentResponse.of(true, AiContentType.WORD_QA, cached.getId(), wordId, wordbookId, content, context.outputSchema(), task.getId(), "SUCCESS", "AI 问答命中缓存"));
                            return;
                        }
                        lockAttempt = tryAcquireWordQaLock(context.cacheKey(), regenerate);
                        if (isLockHeld(lockAttempt)) {
                            throw new BizException(ErrorCode.AI_CALL_FAILED, "相同 AI 回答仍在生成中，请稍后重试");
                        }
                    }

                    if (!regenerate) {
                        WordAiQa cached = findActiveWordQa(context.cacheKey());
                        if (cached != null) {
                            incrementWordQaHit(cached);
                            JsonNode cachedContent = parseJson(cached.getContentJson());
                            asyncTaskService.markSuccess(task.getId(), cached.getId(), "AI 问答命中缓存");
                            taskFinished = true;
                            writeEvent(writer, "status", buildWordQaStatusPayload("CACHE_HIT", "已命中缓存", context.outputSchema(), task));
                            streamCachedAnswer(writer, cachedContent);
                            streamWordQaFieldItems(writer, cachedContent, context.outputSchema());
                            writeEvent(writer, "done", WordAiContentResponse.of(true, AiContentType.WORD_QA, cached.getId(), wordId, wordbookId, cachedContent, context.outputSchema(), task.getId(), "SUCCESS", "AI 问答命中缓存"));
                            return;
                        }
                    }

                    writeEvent(writer, "status", buildWordQaStatusPayload("RUNNING", "正在生成 AI 回答", context.outputSchema(), task));
                    WordQaJsonStreamExtractor streamExtractor = new WordQaJsonStreamExtractor(context.outputSchema(), objectMapper);
                    AiPrompt prompt = buildPrompt(AiContentType.WORD_QA, context.sourceJson(), context.sourceHash(), context.promptTemplate(), STREAM_OUTPUT_CONSTRAINT);
                    AiChatCompletionResult result = generateQuestionStreamWithFallback(userId, prompt, writer, streamExtractor, context.outputSchema(), task.getId());
                    JsonNode content = parseJson(result.content());
                    if (!streamExtractor.hasAnswerEmitted()) {
                        String answer = content.path("answer").asText("");
                        if (!answer.isBlank()) {
                            writeEvent(writer, "chunk", Map.of("text", answer));
                        }
                    }
                    WordAiQa qa = saveWordQaResult(userId, wordId, wordbookId, safeQuestion, context.sourceHash(), context.cacheKey(), content, context.outputSchema());
                    JsonNode savedContent = parseJson(qa.getContentJson());
                    asyncTaskService.markSuccess(task.getId(), qa.getId(), "AI 问答生成完成");
                    taskFinished = true;
                    writeEvent(writer, "done", WordAiContentResponse.of(false, AiContentType.WORD_QA, qa.getId(), wordId, wordbookId, savedContent, context.outputSchema(), task.getId(), "SUCCESS", "AI 问答生成完成"));
                } finally {
                    releaseWordQaLock(lockAttempt);
                }
            }
        } catch (Exception ex) {
            if (taskCreated && task != null && !taskFinished) {
                markWordQaTaskFailed(task, ex);
            }
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

    /**
     * 使用流式生成并支持fallback机制
     * <p>
     * 该方法优先使用流式API生成AI回答，并在流式响应过程中实时推送内容。
     * 如果流式调用失败，会自动降级到普通的非流式JSON生成。
     * </p>
     *
     * @param userId 用户ID
     * @param prompt AI提示词对象
     * @param writer 输出流写入器
     * @param streamExtractor JSON流提取器
     * @param outputSchema 输出JSON schema
     * @param asyncTaskId 异步任务ID
     * @return AiChatCompletionResult AI聊天完成结果
     * @throws Exception 当AI调用失败且无法fallback时抛出异常
     */
    private AiChatCompletionResult generateQuestionStreamWithFallback(
            Long userId,
            AiPrompt prompt,
            OutputStreamWriter writer,
            WordQaJsonStreamExtractor streamExtractor,
            JsonNode outputSchema,
            Long asyncTaskId
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
            }, asyncTaskId);
        } catch (BizException ex) {
            if (ex.getErrorCode() != ErrorCode.AI_CALL_FAILED) {
                throw ex;
            }
            AiChatCompletionResult fallback = aiGatewayService.generateJson(userId, AiContentType.WORD_QA, prompt, asyncTaskId);
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

    /**
     * 构建流式错误响应载荷
     *
     * @param ex 发生的异常
     * @return Map&lt;String, Object&gt; 包含错误码和错误信息的映射
     */
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

    /**
     * 生成单词问答内容（内部方法）
     * <p>
     * 该方法是同步生成问答的核心逻辑，创建或复用异步任务并等待完成。
     * </p>
     *
     * @param userId 用户ID
     * @param wordbookId 单词本ID
     * @param wordId 单词ID
     * @param question 问题内容
     * @param regenerate 是否重新生成
     * @return WordAiContentResponse 问答响应对象
     */
    private WordAiContentResponse generateWordQaContent(Long userId, Long wordbookId, Long wordId, String question, boolean regenerate) {
        WordQaContext context = buildWordQaContext(userId, wordbookId, wordId, question);
        WordQaTaskCreation creation = createOrReuseWordQaTask(userId, context, regenerate);
        AsyncTask task = creation.task();
        if (!creation.created()) {
            return buildWordQaTaskResponse(task, context);
        }
        try {
            asyncTaskService.markRunning(task.getId(), "正在生成 AI 回答", 20);
            return generateWordQaForTask(task, context, regenerate);
        } catch (BizException ex) {
            markWordQaTaskFailed(task, ex);
            throw ex;
        } catch (Exception ex) {
            markWordQaTaskFailed(task, ex);
            throw new BizException(ErrorCode.AI_CALL_FAILED, "AI 问答生成失败，请稍后重试");
        }
    }

    /**
     * 获取启用的单词对象
     * <p>
     * 验证单词是否存在于指定的单词本中且处于启用状态。
     * </p>
     *
     * @param wordbookId 单词本ID
     * @param wordId 单词ID
     * @return Word 启用的单词实体
     * @throws BizException 当单词不存在或未启用时抛出异常
     */
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

        /**
         * 构建单词问答上下文
         * <p>
         * 该方法收集生成AI问答所需的所有上下文信息，包括单词本、单词、用户设置等，
         * 并计算源数据的哈希值用于缓存键生成。
         * </p>
         *
         * @param userId 用户ID
         * @param wordbookId 单词本ID
         * @param wordId 单词ID
         * @param question 用户问题
         * @return WordQaContext 包含所有必要上下文的对象
         */
        private WordQaContext buildWordQaContext(Long userId, Long wordbookId, Long wordId, String question) {
            Wordbook wordbook = wordbookService.getEnabledWordbook(wordbookId);
            Word word = getEnabledWord(wordbookId, wordId);
            UserSettings settings = userService.getOrCreateSettings(userId);
            String sourceJson = buildSourceJson(AiContentType.WORD_QA, wordbook, word, settings, question);
            ResolvedAiPromptTemplate promptTemplate = aiPromptTemplateService.resolve(AiPromptFeatureType.WORD_QA, wordbookId);
            JsonNode outputSchema = outputSchemaService.schemaNode(promptTemplate.outputSchemaJson());
            String sourceHash = sha256(sourceJson + "\n#prompt:" + promptTemplate.cacheFingerprint());
            String cacheKey = buildCacheKey(AiContentType.WORD_QA, wordbookId, wordId, sourceHash);
            return new WordQaContext(wordbookId, wordId, question, sourceJson, sourceHash, cacheKey, promptTemplate, outputSchema);
        }

        /**
         * 为异步任务生成单词问答
         * <p>
         * 该方法在任务执行时被调用，负责实际的AI问答生成逻辑。
         * 实现了双重检查锁定模式来避免重复生成相同的问答内容。
         * </p>
         *
         * @param task 异步任务对象
         * @param context 问答上下文
         * @param regenerate 是否重新生成
         * @return WordAiContentResponse 生成的问答响应
         */
        private WordAiContentResponse generateWordQaForTask(AsyncTask task, WordQaContext context, boolean regenerate) {
            synchronized (wordQaCacheLock(context.cacheKey())) {
                if (!regenerate) {
                    WordAiQa cached = findActiveWordQa(context.cacheKey());
                    if (cached != null) {
                        incrementWordQaHit(cached);
                        asyncTaskService.markSuccess(task.getId(), cached.getId(), "AI 问答命中缓存");
                        return WordAiContentResponse.of(true, AiContentType.WORD_QA, cached.getId(), context.wordId(), context.wordbookId(), parseJson(cached.getContentJson()), context.outputSchema(), task.getId(), "SUCCESS", "AI 问答命中缓存");
                    }
                }

                RedisLockAttempt lockAttempt = tryAcquireWordQaLock(context.cacheKey(), regenerate);
                try {
                    if (isLockHeld(lockAttempt)) {
                        WordAiQa cached = waitForWordQaCache(context.cacheKey(), lockAttempt.lock().key());
                        if (cached != null) {
                            incrementWordQaHit(cached);
                            asyncTaskService.markSuccess(task.getId(), cached.getId(), "AI 问答命中缓存");
                            return WordAiContentResponse.of(true, AiContentType.WORD_QA, cached.getId(), context.wordId(), context.wordbookId(), parseJson(cached.getContentJson()), context.outputSchema(), task.getId(), "SUCCESS", "AI 问答命中缓存");
                        }
                        lockAttempt = tryAcquireWordQaLock(context.cacheKey(), regenerate);
                        if (isLockHeld(lockAttempt)) {
                            throw new BizException(ErrorCode.AI_CALL_FAILED, "相同 AI 回答仍在生成中，请稍后重试");
                        }
                    }

                    if (!regenerate) {
                        WordAiQa cached = findActiveWordQa(context.cacheKey());
                        if (cached != null) {
                            incrementWordQaHit(cached);
                            asyncTaskService.markSuccess(task.getId(), cached.getId(), "AI 问答命中缓存");
                            return WordAiContentResponse.of(true, AiContentType.WORD_QA, cached.getId(), context.wordId(), context.wordbookId(), parseJson(cached.getContentJson()), context.outputSchema(), task.getId(), "SUCCESS", "AI 问答命中缓存");
                        }
                    }

                    AiPrompt prompt = buildPrompt(AiContentType.WORD_QA, context.sourceJson(), context.sourceHash(), context.promptTemplate());
                    AiChatCompletionResult result = aiGatewayService.generateJson(task.getUserId(), AiContentType.WORD_QA, prompt, task.getId());
                    JsonNode content = parseJson(result.content());
                    WordAiQa qa = saveWordQaResult(task.getUserId(), context.wordId(), context.wordbookId(), context.question(), context.sourceHash(), context.cacheKey(), content, context.outputSchema());
                    JsonNode savedContent = parseJson(qa.getContentJson());
                    asyncTaskService.markSuccess(task.getId(), qa.getId(), "AI 问答生成完成");
                    return WordAiContentResponse.of(false, AiContentType.WORD_QA, qa.getId(), context.wordId(), context.wordbookId(), savedContent, context.outputSchema(), task.getId(), "SUCCESS", "AI 问答生成完成");
                } finally {
                    releaseWordQaLock(lockAttempt);
                }
            }
        }

        /**
         * 创建或复用单词问答任务
         * <p>
         * 该方法使用分布式锁来确保任务创建的原子性，避免并发创建重复任务。
         * 首先尝试获取锁，然后在锁保护下检查是否有可复用的任务。
         * </p>
         *
         * @param userId 用户ID
         * @param context 问答上下文
         * @param requestedRegenerate 是否请求重新生成
         * @return WordQaTaskCreation 包含任务和是否为新创建的标识
         */
        private WordQaTaskCreation createOrReuseWordQaTask(Long userId, WordQaContext context, boolean requestedRegenerate) {
            String lockKey = RedisKeys.aiWordQaTaskCreateLockKey(userId, context.wordbookId(), context.wordId(), context.sourceHash());
            RedisLockAttempt lockAttempt = redisDistributedLockService.tryLock(lockKey, WORD_QA_TASK_CREATE_LOCK_TTL);
            if (isLockHeld(lockAttempt)) {
                AsyncTask reusableTask = waitForReusableWordQaTask(userId, context, requestedRegenerate, lockKey);
                if (reusableTask != null) {
                    return new WordQaTaskCreation(reusableTask, false);
                }
                lockAttempt = redisDistributedLockService.tryLock(lockKey, WORD_QA_TASK_CREATE_LOCK_TTL);
                if (isLockHeld(lockAttempt)) {
                    throw new BizException(ErrorCode.ASYNC_TASK_FAILED, "AI 问答任务正在创建，请稍后重试");
                }
            }

            try {
                AsyncTask reusableTask = findReusableWordQaTask(userId, context, requestedRegenerate);
                if (reusableTask != null) {
                    return new WordQaTaskCreation(reusableTask, false);
                }
                return new WordQaTaskCreation(createWordQaTask(userId, context, requestedRegenerate), true);
            } finally {
                releaseWordQaLock(lockAttempt);
            }
        }

        /**
         * 等待可复用的单词问答任务
         * <p>
         * 该方法轮询检查是否有可复用的任务出现，直到超时或锁被释放。
         * </p>
         *
         * @param userId 用户ID
         * @param context 问答上下文
         * @param requestedRegenerate 是否请求重新生成
         * @param lockKey 分布式锁的键
         * @return AsyncTask 可复用的任务，如果超时尚未找到则返回null
         */
        private AsyncTask waitForReusableWordQaTask(Long userId, WordQaContext context, boolean requestedRegenerate, String lockKey) {
            long deadline = System.nanoTime() + WORD_QA_TASK_CREATE_LOCK_WAIT_TIMEOUT.toNanos();
            while (System.nanoTime() < deadline) {
                AsyncTask reusableTask = findReusableWordQaTask(userId, context, requestedRegenerate);
                if (reusableTask != null) {
                    return reusableTask;
                }
                if (!redisDistributedLockService.isLocked(lockKey)) {
                    return null;
                }
                try {
                    Thread.sleep(WORD_QA_TASK_CREATE_LOCK_POLL_INTERVAL.toMillis());
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
            return findReusableWordQaTask(userId, context, requestedRegenerate);
        }

        /**
         * 查找可复用的单词问答任务
         *
         * @param userId 用户ID
         * @param context 问答上下文
         * @param requestedRegenerate 是否请求重新生成
         * @return AsyncTask 可复用的任务，如果不存在则返回null
         */
        private AsyncTask findReusableWordQaTask(Long userId, WordQaContext context, boolean requestedRegenerate) {
            List<AsyncTask> tasks = asyncTaskService.listRecentTasks(userId, AsyncTaskType.AI_WORD_QA, RECENT_REUSABLE_TASK_LIMIT);
            if (tasks == null) {
                return null;
            }
            return tasks.stream()
                    .filter(task -> isReusableWordQaTask(task, context, requestedRegenerate))
                    .findFirst()
                    .orElse(null);
        }

        /**
         * 判断任务是否可复用
         *
         * @param task 待检查的任务任务
         * @param context 问答上下文
         * @param requestedRegenerate 是否请求重新生成
         * @return boolean 如果任务可复用返回true，否则返回false
         */
        private boolean isReusableWordQaTask(AsyncTask task, WordQaContext context, boolean requestedRegenerate) {
            if (task == null || task.getStatus() == AsyncTaskStatus.FAILED) {
                return false;
            }
            if (task.getStatus() == AsyncTaskStatus.SUCCESS && (requestedRegenerate || task.getResultId() == null)) {
                return false;
            }
            WordQaTaskPayload payload = readWordQaTaskPayloadOrNull(task.getRequestJson());
            return payload != null
                    && Objects.equals(payload.wordbookId(), context.wordbookId())
                    && Objects.equals(payload.wordId(), context.wordId())
                    && Objects.equals(payload.question(), context.question())
                    && Objects.equals(payload.sourceHash(), context.sourceHash());
        }

        /**
         * 构建单词问答任务响应
         *
         * @param task 异步任务
         * @param context 问答上下文
         * @return WordAiContentResponse 任务响应对象
         */
        private WordAiContentResponse buildWordQaTaskResponse(AsyncTask task, WordQaContext context) {
            if (task != null && task.getStatus() == AsyncTaskStatus.SUCCESS && task.getResultId() != null) {
                WordAiQa qa = findWordQaById(task.getResultId(), context.wordbookId(), context.wordId());
                if (qa != null) {
                    boolean cacheHit = task.getMessage() != null && task.getMessage().contains("命中缓存");
                    return WordAiContentResponse.of(cacheHit, AiContentType.WORD_QA, qa.getId(), context.wordId(), context.wordbookId(), parseJson(qa.getContentJson()), context.outputSchema(), task.getId(), task.getStatus().name(), task.getMessage());
                }
            }
            return WordAiContentResponse.of(false, AiContentType.WORD_QA, task == null ? null : task.getResultId(), context.wordId(), context.wordbookId(), null, context.outputSchema(), task == null ? null : task.getId(), task == null || task.getStatus() == null ? null : task.getStatus().name(), task == null ? null : task.getMessage());
        }

        /**
         * 流式输出可复用的任务结果
         *
         * @param writer 输出流写入器
         * @param task 异步任务
         * @param context 问答上下文
         * @throws Exception IO异常
         */
        private void streamReusableWordQaTask(OutputStreamWriter writer, AsyncTask task, WordQaContext context) throws Exception {
            WordAiContentResponse response = buildWordQaTaskResponse(task, context);
            String status = response.taskStatus() == null ? "RUNNING" : response.taskStatus();
            writeEvent(writer, "status", buildWordQaStatusPayload(status, response.taskMessage(), context.outputSchema(), task));
            if (response.content() == null) {
                return;
            }
            streamCachedAnswer(writer, response.content());
            streamWordQaFieldItems(writer, response.content(), context.outputSchema());
            writeEvent(writer, "done", response);
        }

        /**
         * 准备处理单词问答任务
         * <p>
         * 检查任务状态并将其标记为运行中（如果当前是PENDING状态）。
         * 对于重投的任务，如果是RUNNING状态也允许继续处理。
         * </p>
         *
         * @param task 异步任务
         * @param redelivered 是否为重投任务
         * @return boolean 如果任务可以处理返回true，否则返回false
         */
        private boolean prepareWordQaTaskForProcessing(AsyncTask task, boolean redelivered) {
            if (task.getStatus() == AsyncTaskStatus.PENDING) {
                return asyncTaskService.markRunningIfPending(task.getId(), "正在生成 AI 回答", 20);
            }
            if (task.getStatus() == AsyncTaskStatus.RUNNING && redelivered) {
                log.warn("恢复处理 RabbitMQ 重投的 AI 问答任务，taskId={}", task.getId());
                return true;
            }
            return false;
        }

        /**
         * 判断任务状态是否为终态
         *
         * @param status 任务状态
         * @return boolean 如果是SUCCESS或FAILED返回true，否则返回false
         */
        private boolean isTerminalStatus(AsyncTaskStatus status) {
            return status == AsyncTaskStatus.SUCCESS || status == AsyncTaskStatus.FAILED;
        }

        /**
         * 解析单词问答任务载荷
         *
         * @param requestJson 请求JSON字符串
         * @return WordQaTaskPayload 解析后的任务载荷
         * @throws BizException 当JSON格式错误时抛出异常
         */
        private WordQaTaskPayload parseWordQaTaskPayload(String requestJson) {
            WordQaTaskPayload payload = readWordQaTaskPayloadOrNull(requestJson);
            if (payload == null) {
                throw new BizException(ErrorCode.BAD_REQUEST, "AI 问答任务参数格式错误");
            }
            return payload;
        }

        /**
         * 尝试读取单词问答任务载荷（不抛异常）
         *
         * @param requestJson 请求JSON字符串
         * @return WordQaTaskPayload 解析后的任务载荷，如果解析失败返回null
         */
        private WordQaTaskPayload readWordQaTaskPayloadOrNull(String requestJson) {
            if (!StringUtils.hasText(requestJson)) {
                return null;
            }
            try {
                JsonNode root = objectMapper.readTree(requestJson);
                Long wordbookId = readLong(root.path("wordbookId"));
                Long wordId = readLong(root.path("wordId"));
                String question = root.path("question").asText("");
                String sourceHash = root.path("sourceHash").asText("");
                boolean regenerate = root.path("regenerate").asBoolean(false);
                if (wordbookId == null || wordId == null || !StringUtils.hasText(question) || !StringUtils.hasText(sourceHash)) {
                    return null;
                }
                return new WordQaTaskPayload(wordbookId, wordId, question, sourceHash, regenerate);
            } catch (Exception ex) {
                return null;
            }
        }

        /**
         * 从JSON节点读取Long值
         *
         * @param node JSON节点
         * @return Long 解析后的Long值，如果无法解析返回null
         */
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

        /**
         * 创建单词问答异步任务
         *
         * @param userId 用户ID
         * @param context 问答上下文
         * @param regenerate 是否重新生成
         * @return AsyncTask 创建的异步任务对象
         */
        private AsyncTask createWordQaTask(Long userId, WordQaContext context, boolean regenerate) {
            String requestJson = toJson(Map.of(
                    "wordbookId", String.valueOf(context.wordbookId()),
                    "wordId", String.valueOf(context.wordId()),
                    "question", context.question(),
                    "sourceHash", context.sourceHash(),
                    "regenerate", regenerate
            ));
            return asyncTaskService.createTask(userId, AsyncTaskType.AI_WORD_QA, requestJson);
        }

        /**
         * 根据结果ID查找单词问答记录
         *
         * @param resultId 结果ID
         * @param wordbookId 单词本ID
         * @param wordId 单词ID
         * @return WordAiQa 找到的问答记录，如果不存在返回null
         */
        private WordAiQa findWordQaById(Long resultId, Long wordbookId, Long wordId) {
            if (resultId == null) {
                return null;
            }
            return wordAiQaMapper.selectOne(new LambdaQueryWrapper<WordAiQa>()
                    .eq(WordAiQa::getId, resultId)
                    .eq(WordAiQa::getWordbookId, wordbookId)
                    .eq(WordAiQa::getWordId, wordId)
                    .eq(WordAiQa::getDeleted, 0)
                    .last("LIMIT 1"));
        }

        /**
         * 查找活跃的缓存问答记录
         *
         * @param cacheKey 缓存键
         * @return WordAiQa 活跃的问答记录，如果不存在返回null
         */
        private WordAiQa findActiveWordQa(String cacheKey) {
            return wordAiQaMapper.selectOne(new QueryWrapper<WordAiQa>()
                    .eq("cache_key", cacheKey)
                    .eq("cache_active", true)
                    .eq("deleted", 0)
                    .last("LIMIT 1"));
        }

        /**
         * 根据缓存键获取对应的分段锁对象
         *
         * @param cacheKey 缓存键
         * @return Object 锁对象
         */
        private Object wordQaCacheLock(String cacheKey) {
            return wordQaCacheLocks[Math.floorMod(cacheKey.hashCode(), wordQaCacheLocks.length)];
        }

        /**
         * 尝试获取单词问答分布式锁
         *
         * @param cacheKey 缓存键
         * @param regenerate 是否重新生成
         * @return RedisLockAttempt 锁尝试结果
         */
        private RedisLockAttempt tryAcquireWordQaLock(String cacheKey, boolean regenerate) {
            if (regenerate) {
                return RedisLockAttempt.unavailable(RedisKeys.aiLockKey(AiContentType.WORD_QA, cacheKey));
            }
            return redisDistributedLockService.tryLock(RedisKeys.aiLockKey(AiContentType.WORD_QA, cacheKey), AI_CACHE_LOCK_TTL);
        }

        /**
         * 判断锁是否被持有（未被当前请求获取但仍被其他人持有）
         *
         * @param lockAttempt 锁尝试结果
         * @return boolean 如果锁被其他人持有返回true
         */
        private boolean isLockHeld(RedisLockAttempt lockAttempt) {
            return lockAttempt != null && !lockAttempt.acquired() && !lockAttempt.unavailable();
        }

        /**
         * 释放分布式锁
         *
         * @param lockAttempt 锁尝试结果
         */
        private void releaseWordQaLock(RedisLockAttempt lockAttempt) {
            if (lockAttempt != null && lockAttempt.acquired()) {
                redisDistributedLockService.release(lockAttempt.lock());
            }
        }

        /**
         * 等待缓存中的问答结果出现
         * <p>
         * 该方法轮询检查缓存中是否出现了所需的问答结果，直到超时或锁被释放。
         * </p>
         *
         * @param cacheKey 缓存键
         * @param lockKey 分布式锁的键
         * @return WordAiQa 找到的问答记录，如果超时仍未找到返回null
         */
        private WordAiQa waitForWordQaCache(String cacheKey, String lockKey) {
            long deadline = System.nanoTime() + AI_CACHE_LOCK_WAIT_TIMEOUT.toNanos();
            while (System.nanoTime() < deadline) {
                sleepForCachePoll();
                WordAiQa cached = findActiveWordQa(cacheKey);
                if (cached != null) {
                    return cached;
                }
                if (!redisDistributedLockService.isLocked(lockKey)) {
                    break;
                }
            }
            return findActiveWordQa(cacheKey);
        }

        /**
         * 休眠等待缓存轮询间隔
         *
         * @throws BizException 当线程被中断时抛出异常
         */
        private void sleepForCachePoll() {
            try {
                Thread.sleep(AI_CACHE_LOCK_POLL_INTERVAL.toMillis());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new BizException(ErrorCode.AI_CALL_FAILED, "等待相同 AI 内容生成时被中断");
            }
        }

        /**
         * 增加问答记录的命中次数
         * <p>
         * 使用缓冲机制批量更新命中次数，减少数据库写操作。
         * </p>
         *
         * @param qa 问答记录对象
         */
        private void incrementWordQaHit(WordAiQa qa) {
            if (qa == null || qa.getId() == null) {
                return;
            }
            qa.setHitCount((qa.getHitCount() == null ? 0 : qa.getHitCount()) + 1);
            if (redisAiHitCountBuffer.incrementHit(AiContentType.WORD_QA, qa.getId())) {
                return;
            }
            transactionTemplate.executeWithoutResult(status -> {
                wordAiQaMapper.updateById(qa);
            });
        }

        /**
         * 保存单词问答结果
         * <p>
         * 该方法将AI生成的问答结果保存到数据库，并停用旧的缓存记录。
         * 使用事务确保数据一致性，处理可能的唯一键冲突。
         * </p>
         *
         * @param userId 用户ID
         * @param wordId 单词ID
         * @param wordbookId 单词本ID
         * @param question 问题内容
         * @param sourceHash 源数据哈希
         * @param cacheKey 缓存键
         * @param content JSON格式的内容
         * @param outputSchema 输出schema
         * @return WordAiQa 保存的问答记录
         */
        private WordAiQa saveWordQaResult(
                Long userId,
                Long wordId,
                Long wordbookId,
                String question,
                String sourceHash,
                String cacheKey,
                JsonNode content,
                JsonNode outputSchema
        ) {
            return transactionTemplate.execute(status -> {
                deactivateWordQaCache(cacheKey);
                WordAiQa qa = new WordAiQa();
                qa.setCreatedByUserId(userId);
                qa.setWordId(wordId);
                qa.setWordbookId(wordbookId);
                qa.setQuestion(question);
                qa.setSourceHash(sourceHash);
                qa.setCacheKey(cacheKey);
                qa.setContentJson(toJson(content));
                qa.setOutputSchemaJson(outputSchema == null ? null : toJson(outputSchema));
                qa.setCacheActive(true);
                qa.setHitCount(0);
                qa.setDeleted(0);
                try {
                    wordAiQaMapper.insert(qa);
                    return qa;
                } catch (DuplicateKeyException ignored) {
                    WordAiQa active = findActiveWordQa(cacheKey);
                    if (active != null) {
                        return active;
                    }
                    throw ignored;
                }
            });
        }

        /**
         * 停用指定缓存键的所有活跃问答记录
         *
         * @param cacheKey 缓存键
         */
        private void deactivateWordQaCache(String cacheKey) {
            wordAiQaMapper.update(null, new UpdateWrapper<WordAiQa>()
                    .set("cache_active", null)
                    .eq("cache_key", cacheKey)
                    .eq("cache_active", true)
                    .eq("deleted", 0));
        }

        /**
         * 标记单词问答任务为失败
         *
         * @param task 异步任务
         * @param ex 导致失败的异常
         */
        private void markWordQaTaskFailed(AsyncTask task, Exception ex) {
            if (task == null) {
                return;
            }
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

        /**
         * 构建AI提示词对象
         *
         * @param contentType 内容类型
         * @param sourceJson 源数据JSON
         * @param sourceHash 源数据哈希
         * @param promptTemplate 提示词模板
         * @return AiPrompt 构建的提示词对象
         */
        private AiPrompt buildPrompt(AiContentType contentType, String sourceJson, String sourceHash, ResolvedAiPromptTemplate promptTemplate) {
            return buildPrompt(contentType, sourceJson, sourceHash, promptTemplate, null);
        }

        /**
         * 构建AI提示词对象（带额外指令）
         *
         * @param contentType 内容类型
         * @param sourceJson 源数据JSON
         * @param sourceHash 源数据哈希
         * @param promptTemplate 提示词模板
         * @param extraInstruction 额外的指令
         * @return AiPrompt 构建的提示词对象
         */
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
                case WORD_QA -> "输出 JSON 字段：answer 字符串；keyPoints 字符串数组；relatedWords 字符串数组；followUps 字符串数组；grammarTip 字符串。回答必须直接回应用户问题，不能编造未给出的固定知识；如果问题超出单词学习范围，请简短说明并拉回该单词。grammarTip 最后补充一条零基础英语语法小知识，优先相关，否则由你自由生成。允许 answer 和 grammarTip 里的字符串适当使用 Markdown 语法。";
                default -> throw new BizException(ErrorCode.BAD_REQUEST, "不支持的 AI 单词内容类型");
            };
            String userPrompt = schema + "\n请基于以下单词上下文生成内容，避免编造不存在的固定搭配。\n" + sourceJson;
            if (extraInstruction != null && !extraInstruction.isBlank()) {
                userPrompt = userPrompt + "\n" + extraInstruction.trim();
            }
            return new AiPrompt(SYSTEM_PROMPT, userPrompt, sourceHash);
        }

        /**
         * 构建托管提示词
         *
         * @param promptTemplate 提示词模板
         * @param sourceJson 源数据JSON
         * @param extraInstruction 额外指令
         * @return String 构建的提示词字符串
         */
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

        /**
         * 构建源数据JSON
         *
         * @param contentType 内容类型
         * @param wordbook 单词本
         * @param word 单词
         * @param settings 用户设置
         * @param question 问题
         * @return String 源数据JSON字符串
         */
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

        /**
         * 构建单词问答的源数据JSON
         *
         * @param word 单词
         * @param settings 用户设置
         * @param question 问题
         * @return String 源数据JSON字符串
         */
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

        /**
         * 构建缓存键
         *
         * @param contentType 内容类型
         * @param wordbookId 单词本ID
         * @param wordId 单词ID
         * @param sourceHash 源数据哈希
         * @return String 缓存键
         */
        private String buildCacheKey(AiContentType contentType, Long wordbookId, Long wordId, String sourceHash) {
            return contentType.name().toLowerCase(Locale.ROOT)
                    + ":wordbook:" + wordbookId
                    + ":word:" + wordId
                    + ":" + sourceHash.substring(0, 24);
        }

        /**
         * 流式输出缓存的答案
         *
         * @param writer 输出流写入器
         * @param content JSON内容节点
         * @throws Exception IO异常
         */
        private void streamCachedAnswer(OutputStreamWriter writer, JsonNode content) throws Exception {
            String answer = content.path("answer").asText("");
            streamText(writer, answer);
        }

        /**
         * 构建单词问答状态载荷
         *
         * @param status 状态
         * @param message 消息
         * @param outputSchema 输出schema
         * @return Map&lt;String, Object&gt; 状态载荷映射
         */
        private Map<String, Object> buildWordQaStatusPayload(String status, String message, JsonNode outputSchema) {
            return buildWordQaStatusPayload(status, message, outputSchema, null);
        }

        /**
         * 构建单词问答状态载荷（带任务信息）
         *
         * @param status 状态
         * @param message 消息
         * @param outputSchema 输出schema
         * @param task 异步任务
         * @return Map&lt;String, Object&gt; 状态载荷映射
         */
        private Map<String, Object> buildWordQaStatusPayload(String status, String message, JsonNode outputSchema, AsyncTask task) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("status", status);
            payload.put("message", message);
            payload.put("outputSchema", outputSchema);
            if (task != null) {
                payload.put("taskId", String.valueOf(task.getId()));
                payload.put("taskStatus", task.getStatus() == null ? status : task.getStatus().name());
                if (task.getResultId() != null) {
                    payload.put("resultId", String.valueOf(task.getResultId()));
                }
            }
            return payload;
        }

        /**
         * 流式输出单词问答的字段项
         *
         * @param writer 输出流写入器
         * @param content JSON内容节点
         * @param outputSchema 输出schema
         * @throws Exception IO异常
         */
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

        /**
         * 判断字段项是否可显示
         *
         * @param item JSON节点
         * @return boolean 如果可显示返回true
         */
        private static boolean isDisplayableFieldItem(JsonNode item) {
            if (item == null || item.isNull() || item.isMissingNode()) {
                return false;
            }
            return !item.isTextual() || !item.asText("").isBlank();
        }

        /**
         * 获取单词问答的数组字段名
         *
         * @param outputSchema 输出schema
         * @return Set&lt;String&gt; 数组字段名集合
         */
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

        /**
         * 流式输出文本
         *
         * @param writer 输出流写入器
         * @param answer 答案文本
         * @throws Exception IO异常
         */
        private void streamText(OutputStreamWriter writer, String answer) throws Exception {
            int index = 0;
            while (index < answer.length()) {
                int next = Math.min(index + 12, answer.length());
                writeEvent(writer, "chunk", Map.of("text", answer.substring(index, next)));
                index = next;
            }
        }

        /**
         * 写入SSE事件
         *
         * @param writer 输出流写入器
         * @param event 事件名称
         * @param data 事件数据
         * @throws Exception IO异常
         */
        private void writeEvent(OutputStreamWriter writer, String event, Object data) throws Exception {
            writer.write("event: " + event + "\n");
            writer.write("data: " + toSseDataJson(data) + "\n\n");
            writer.flush();
        }

        /**
         * 转换为SSE数据格式的JSON
         *
         * @param data 数据对象
         * @return String JSON字符串
         */
        private String toSseDataJson(Object data) {
            return toJson(data);
        }

        /**
         * 解析JSON字符串
         *
         * @param contentJson JSON字符串
         * @return JsonNode 解析后的JSON节点
         */
        private JsonNode parseJson(String contentJson) {
            return AiJsonUtils.parseObject(objectMapper, contentJson);
        }

        /**
         * 将对象序列化为JSON字符串
         *
         * @param value 要序列化的对象
         * @return String JSON字符串
         * @throws BizException 当序列化失败时抛出异常
         */
        private String toJson(Object value) {
            try {
                return objectMapper.writeValueAsString(value);
            } catch (Exception ex) {
                throw new BizException(ErrorCode.INTERNAL_ERROR);
            }
        }

        /**
         * 计算SHA-256哈希值
         *
         * @param value 要哈希的字符串
         * @return String 十六进制格式的哈希值
         * @throws BizException 当哈希计算失败时抛出异常
         */
        private String sha256(String value) {
            try {
                byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
                return HexFormat.of().formatHex(digest);
            } catch (Exception ex) {
                throw new BizException(ErrorCode.INTERNAL_ERROR);
            }
        }

        /**
         * 安全地获取字符串值，null时返回空字符串
         *
         * @param value 原始字符串
         * @return String 安全的字符串，不会为null
         */
        private String safe(String value) {
            return value == null ? "" : value;
        }




        /**
         * 流式JSON抽取器，用于从AI模型的流式输出中提取问答字段内容。
         *
         * <p>该类负责协调提取answer字符串字段和多个数组类型字段，支持跨数据分片的增量解析。</p>
         */
        private static final class WordQaJsonStreamExtractor {

            private final JsonStringFieldExtractor answerExtractor = new JsonStringFieldExtractor("answer");
            private final List<JsonArrayFieldExtractor> itemExtractors;

            /**
             * 构造流式JSON抽取器。
             *
             * @param outputSchema 输出模式定义，包含需要提取的数组字段信息
             * @param objectMapper JSON对象映射器，用于解析数组项
             */
            WordQaJsonStreamExtractor(JsonNode outputSchema, ObjectMapper objectMapper) {
                this.itemExtractors = wordQaArrayFields(outputSchema).stream()
                        .map(field -> new JsonArrayFieldExtractor(field, objectMapper))
                        .toList();
            }

            /**
             * 追加流式数据分片并提取字段内容。
             *
             * @param delta 新的数据分片
             * @return 包含提取的answer和数组字段项的增量结果
             */
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

            /**
             * 检查是否已开始输出answer内容。
             *
             * @return true表示已输出过answer内容
             */
            boolean hasAnswerEmitted() {
                return answerExtractor.hasEmitted();
            }
        }

    /**
     * 流式 JSON 字符串字段提取器
     * <p>
     * 用于从 AI 模型的流式输出中提取指定 key 的字符串值（例如 "answer"）。
     * 支持跨数据分片（delta）的状态保持和增量解析。
     * </p>
     */
    private static final class JsonStringFieldExtractor {

        private final String key;
        private final String quotedKey;
        private final StringBuilder buffer = new StringBuilder();
        /**
         * 状态机状态：
         * 0: 查找 Key
         * 1: 查找冒号
         * 2: 查找起始引号
         * 3: 读取字符串内容
         * 4: 完成
         */
        private int state = 0;
        private int index = 0;
        private boolean emitted = false;

        private JsonStringFieldExtractor(String key) {
            this.key = key;
            this.quotedKey = "\"" + key + "\"";
        }

        /**
         * 追加新的数据分片并提取内容
         *
         * @param delta 新的数据分片
         * @return 提取到的字符串内容
         */
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
                // 防止死循环保护机制：
                // 当遇到跨分片的转义字符（如 \n 被拆分为 \ 和 n两个分片）时，
                // readString 会将 index 回退以等待下一个分片。
                // 如果本轮循环 index 没有推进，且 buffer 还有剩余未处理数据（state != 0），
                // 则说明当前分片不足以完成当前状态转换，必须跳出循环等待下一个 delta。
                // 否则会导致在同一个位置无限循环，造成后端卡死和前端 SSE 连接异常中断。
                // readString 遇到跨 delta 的转义字符时会把 index 回退到反斜杠等待下一段内容。
                // 如果这里不检查本轮是否推进过 index，answer 里的 \n 在分片边界处可能让循环反复读取同一位置，导致死循环
                // 最终表现为后端流式抽取卡死，前端只能看到换行前的半截回答。
                //谁能想到这里的死循环会导致前端sse截断中止在\n\n呢?（说是中止在\n\n有点误导，其实是中止在第一个\n，ai模型流式输出时，第一个\n分成\和n两块了，第二个\n是完整输出没有分成两块，第一个\n分成两块，导致死循环，正好前端表现为遇到\n\n截断（同样有些误导，应该是表现为前端遇到第一个\n截断，和sse停止条件一样，因此误导了排查方向）
                if (state != 0 && index == prevIndex) {
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

        /**
         * 状态 0: 查找目标 Key
         */
        private void findKey() {
            int found = buffer.indexOf(quotedKey, Math.max(0, index - quotedKey.length()));
            if (found < 0) {
                // 未找到 Key，保留末尾可能匹配的部分，移动 index
                index = Math.max(0, buffer.length() - quotedKey.length());
                return;
            }
            index = found + quotedKey.length();
            state = 1;
        }

        /**
         * 状态 1: 跳过空白字符，查找冒号
         */
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
                // 如果不是冒号也不是空白，说明格式错误，保持状态或进入错误处理（此处简化为继续查找或停止）
            }
        }

        /**
         * 状态 2: 跳过空白字符，查找起始引号
         */
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

        /**
         * 状态 3: 读取字符串内容直到遇到结束引号
         * <p>
         * 处理转义字符，包括跨分片的转义序列。
         * </p>
         *
         * @param output 输出构建器
         */
        private void readString(StringBuilder output) {
            while (index < buffer.length()) {
                char ch = buffer.charAt(index++);
                if (ch == '"') {
                    state = 4;
                    return;
                }
                if (ch == '\\') {
                    // 检查是否是分片边界处的反斜杠
                    if (index >= buffer.length()) {
                        // 反斜杠是当前分片的最后一个字符，回退 index 等待下一个分片
                        index--;
                        return;
                    }
                    // 检查 unicode 转义 '\\uXXXX' 是否完整
                    if (buffer.charAt(index) == 'u' && index + 4 >= buffer.length()) {
                        // '\\u' 后面不足4位，回退 index 等待下一个分片
                        index--;
                        return;
                    }
                    // 处理转义字符
                    appendEscapedChar(output, buffer, index);
                    index = advanceEscapedIndex(buffer, index);
                } else {
                    output.append(ch);
                }
            }
        }
    }

    /**
     * 流式 JSON 数组字段提取器
     * <p>
     * 用于从 AI 模型的流式输出中提取指定 key 的数组类型字段内容（例如 "keyPoints", "relatedWords"）。
     * 支持跨数据分片（delta）的状态保持和增量解析，能够处理嵌套对象、字符串转义以及跨分片的边界情况。
     * </p>
     */
    private static final class JsonArrayFieldExtractor {

        private final String field;
        private final String quotedKey;
        private final ObjectMapper objectMapper;
        private final StringBuilder buffer = new StringBuilder();
        private final StringBuilder currentItem = new StringBuilder();
        /**
         * 状态机状态：
         * 0: 查找 Key
         * 1: 查找冒号
         * 2: 查找起始方括号 '['
         * 3: 寻找数组项开始
         * 4: 读取数组项内容
         * 5: 完成/终止
         */
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

        /**
         * 追加新的数据分片并提取数组项
         *
         * @param delta 新的数据分片
         * @return 提取到的 JSON 节点列表
         */
        List<JsonNode> append(String delta) {
            if (delta == null || delta.isEmpty() || state == 5) {
                return List.of();
            }
            buffer.append(delta);
            List<JsonNode> items = new ArrayList<>();
            // 防止死循环保护机制：
            // 当遇到跨分片的转义字符或未完成的结构时，index 可能不会推进。
            // 如果本轮循环 index 没有变化，说明当前分片不足以完成状态转换，必须跳出等待下一个 delta。
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
                // 如果状态回到初始查找阶段，或者 index 未推进，则中断循环等待更多数据
                if (state == 0 || index >= buffer.length() || index == prevIndex) {
                    break;
                }
            }
            return items;
        }

        /**
         * 状态 0: 查找目标 Key
         */
        private void findKey() {
            int found = buffer.indexOf(quotedKey, Math.max(0, index - quotedKey.length()));
            if (found < 0) {
                // 未找到 Key，保留末尾可能匹配的部分，移动 index
                index = Math.max(0, buffer.length() - quotedKey.length());
                return;
            }
            index = found + quotedKey.length();
            state = 1;
        }

        /**
         * 状态 1: 跳过空白字符，查找冒号
         */
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

        /**
         * 状态 2: 跳过空白字符，查找起始方括号 '['
         */
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

        /**
         * 状态 3: 寻找数组中的下一个有效项
         * <p>
         * 跳过逗号、空白字符，直到遇到 ']'（结束）或有效字符（开始新项）。
         * </p>
         */
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
                // 遇到有效字符，回退一位并开始读取该项
                resetItemState();
                index--;
                state = 4;
                readArrayItem(items);
                return;
            }
        }

        /**
         * 状态 4: 读取单个数组项的内容
         * <p>
         * 通过跟踪嵌套深度和字符串状态来准确识别项的边界。
         * 支持字符串、对象和数组类型的数组项。
         * </p>
         */
        private void readArrayItem(List<JsonNode> items) {
            while (index < buffer.length()) {
                char ch = buffer.charAt(index++);
                
                // 检查是否到达项的边界（仅在非字符串且无嵌套时有效）
                if (!inString && nestedDepth == 0 && (ch == ',' || ch == ']')) {
                    emitCurrentItem(items);
                    state = ch == ']' ? 5 : 3;
                    return;
                }

                currentItem.append(ch);

                // 处理字符串内部逻辑
                if (inString) {
                    if (escaped) {
                        escaped = false;
                    } else if (ch == '\\') {
                        escaped = true;
                    } else if (ch == '"') {
                        inString = false;
                        // 如果整个项只是一个顶层字符串（如 ["hello"]），遇到结束引号即完成
                        if (topLevelString && nestedDepth == 0) {
                            emitCurrentItem(items);
                            state = 3;
                            return;
                        }
                    }
                    continue;
                }

                // 处理非字符串部分的逻辑
                if (ch == '"') {
                    inString = true;
                    // 判断是否为顶层字符串项的开始
                    topLevelString = currentItem.toString().trim().equals("\"");
                } else if (ch == '{' || ch == '[') {
                    nestedDepth++;
                } else if (ch == '}' || ch == ']') {
                    if (nestedDepth > 0) {
                        nestedDepth--;
                    }
                    // 嵌套归零且遇到闭合符号，说明一个复杂对象/数组项结束
                    if (nestedDepth == 0) {
                        emitCurrentItem(items);
                        state = 3;
                        return;
                    }
                }
            }
        }

        /**
         * 重置当前项的解析状态
         */
        private void resetItemState() {
            currentItem.setLength(0);
            inString = false;
            escaped = false;
            nestedDepth = 0;
            topLevelString = false;
        }

        /**
         * 尝试解析并输出当前累积的项
         *
         * @param items 结果列表
         */
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

    /**
     * 处理转义字符并将其追加到输出缓冲区。
     *
     * <p>该方法支持Unicode转义序列（\\uXXXX）和普通转义字符的处理：
     * <ul>
     *   <li>对于Unicode转义，解析4位十六进制数并转换为对应字符</li>
     *   <li>如果十六进制格式错误，则原样保留转义序列</li>
     *   <li>对于普通转义字符，调用decode方法进行解码</li>
     * </ul></p>
     *
     * @param output 用于输出转义字符解码结果的缓冲区
     * @param buffer 包含转义序列的源缓冲区
     * @param escapeIndex 反斜杠后第一个字符的索引位置（即转义类型字符的位置）
     */
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

    /**
     * 计算转义序列处理后的下一个索引位置
     * <p>
     * 该方法用于在解析JSON字符串时，根据当前转义字符的类型，计算解析完成后应该移动到的下一个字符索引。
     * 对于Unicode转义序列（\\uXXXX），需要跳过5个字符（u + 4位十六进制数）；
     * 对于普通转义字符，只需要跳过1个字符。
     * </p>
     *
     * @param buffer 包含转义序列的源缓冲区
     * @param escapeIndex 反斜杠后第一个字符的索引位置（即转义类型字符的位置）
     * @return int 处理完当前转义序列后的下一个索引位置
     */
    private static int advanceEscapedIndex(StringBuilder buffer, int escapeIndex) {
        if (buffer.charAt(escapeIndex) == 'u' && escapeIndex + 4 < buffer.length()) {
            return escapeIndex + 5;
        }
        return escapeIndex + 1;
    }

    /**
     * 解码JSON转义字符
     * <p>
     * 将JSON字符串中的转义字符转换为其对应的实际字符值。
     * 支持标准的JSON转义序列，包括引号、反斜杠、斜杠、退格、换页、换行、回车和制表符。
     * 如果遇到未知的转义字符，则原样返回该字符。
     * </p>
     *
     * @param escaped 转义字符（例如 'n', 't', '"' 等）
     * @return char 解码后的实际字符
     */
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

    /**
     * 单词问答上下文记录
     * <p>
     * 封装生成AI问答所需的所有上下文信息，包括单词本、单词、用户问题、
     * 源数据JSON、哈希值、缓存键、提示词模板和输出Schema。
     * </p>
     *
     * @param wordbookId 单词本ID
     * @param wordId 单词ID
     * @param question 用户问题
     * @param sourceJson 源数据JSON字符串
     * @param sourceHash 源数据SHA-256哈希值，用于缓存键生成
     * @param cacheKey 缓存键，用于查找已生成的问答结果
     * @param promptTemplate 解析后的AI提示词模板
     * @param outputSchema 输出JSON Schema定义
     */
    private record WordQaContext(
            Long wordbookId,
            Long wordId,
            String question,
            String sourceJson,
            String sourceHash,
            String cacheKey,
            ResolvedAiPromptTemplate promptTemplate,
            JsonNode outputSchema
    ) {
    }

    /**
     * 单词问答任务载荷记录
     * <p>
     * 封装异步任务请求中的关键参数，用于验证任务上下文的一致性和复用判断。
     * </p>
     *
     * @param wordbookId 单词本ID
     * @param wordId 单词ID
     * @param question 用户问题
     * @param sourceHash 源数据SHA-256哈希值
     * @param regenerate 是否强制重新生成
     */
    private record WordQaTaskPayload(
            Long wordbookId,
            Long wordId,
            String question,
            String sourceHash,
            boolean regenerate
    ) {
    }

    /**
     * 单词问答任务创建结果记录
     * <p>
     * 封装任务创建或复用的结果，包含任务实体和是否为新创建的标识。
     * </p>
     *
     * @param task 异步任务对象
     * @param created true表示新创建了任务，false表示复用了现有任务
     */
    private record WordQaTaskCreation(AsyncTask task, boolean created) {
    }

    /**
     * 单词问答流式增量数据记录
     * <p>
     * 封装从AI流式响应中提取的增量数据，包括answer字段的文本增量和数组字段的项增量。
     * </p>
     *
     * @param answer 提取到的answer字段文本增量
     * @param items 提取到的数组字段项列表
     */
    private record WordQaStreamDelta(String answer, List<WordQaFieldItem> items) {
    }

    /**
     * 单词问答字段项记录
     * <p>
     * 封装从AI响应中提取的单个数组字段项，包含字段名和该项的JSON内容。
     * </p>
     *
     * @param field 字段名（例如 "keyPoints", "relatedWords"）
     * @param item 字段项的JSON节点
     */
    private record WordQaFieldItem(String field, JsonNode item) {
    }
}
