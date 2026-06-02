package com.lexiflow.quiz.cloze.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexiflow.ai.content.domain.AiContentType;
import com.lexiflow.ai.core.dto.AiChatCompletionResult;
import com.lexiflow.ai.core.dto.AiPrompt;
import com.lexiflow.ai.core.service.AiGatewayService;
import com.lexiflow.ai.core.util.AiJsonUtils;
import com.lexiflow.ai.prompt.domain.AiPromptFeatureType;
import com.lexiflow.ai.prompt.service.AiPromptOutputSchemaService;
import com.lexiflow.ai.prompt.service.AiPromptTemplateService;
import com.lexiflow.ai.prompt.service.ResolvedAiPromptTemplate;
import com.lexiflow.async.domain.AsyncTask;
import com.lexiflow.async.domain.AsyncTaskType;
import com.lexiflow.async.service.AsyncTaskService;
import com.lexiflow.common.error.ErrorCode;
import com.lexiflow.common.exception.BizException;
import com.lexiflow.infra.redis.RedisAiHitCountBuffer;
import com.lexiflow.infra.redis.RedisDistributedLockService;
import com.lexiflow.infra.redis.RedisKeys;
import com.lexiflow.infra.redis.RedisLockAttempt;
import com.lexiflow.quiz.cloze.domain.ClozeAttempt;
import com.lexiflow.quiz.cloze.domain.ClozeAttemptAnswer;
import com.lexiflow.quiz.cloze.domain.ClozeQuiz;
import com.lexiflow.quiz.cloze.domain.ClozeQuizBlank;
import com.lexiflow.quiz.cloze.domain.ClozeSourceType;
import com.lexiflow.quiz.cloze.dto.ClozeAttemptAnswerResponse;
import com.lexiflow.quiz.cloze.dto.ClozeDefinitionGroupResponse;
import com.lexiflow.quiz.cloze.dto.ClozeAttemptResponse;
import com.lexiflow.quiz.cloze.dto.ClozeBlankResponse;
import com.lexiflow.quiz.cloze.dto.ClozeQuizResponse;
import com.lexiflow.quiz.cloze.dto.CreateClozeTaskRequest;
import com.lexiflow.quiz.cloze.dto.CreateClozeTaskResponse;
import com.lexiflow.quiz.cloze.dto.SubmitClozeAttemptRequest;
import com.lexiflow.quiz.cloze.mapper.ClozeAttemptAnswerMapper;
import com.lexiflow.quiz.cloze.mapper.ClozeAttemptMapper;
import com.lexiflow.quiz.cloze.mapper.ClozeQuizBlankMapper;
import com.lexiflow.quiz.cloze.mapper.ClozeQuizMapper;
import com.lexiflow.study.progress.domain.StudyEvent;
import com.lexiflow.study.progress.domain.StudyFeedback;
import com.lexiflow.study.progress.domain.StudyScene;
import com.lexiflow.study.progress.domain.WrongWord;
import com.lexiflow.study.progress.mapper.StudyEventMapper;
import com.lexiflow.study.progress.mapper.WrongWordMapper;
import com.lexiflow.study.progress.service.SpacedRepetitionService;
import com.lexiflow.study.task.domain.DailyTask;
import com.lexiflow.study.task.domain.DailyTaskItem;
import com.lexiflow.study.task.domain.DailyTaskItemStatus;
import com.lexiflow.study.task.domain.DailyTaskItemType;
import com.lexiflow.study.task.domain.DailyTaskStatus;
import com.lexiflow.study.task.domain.DailyTaskType;
import com.lexiflow.study.task.mapper.DailyTaskItemMapper;
import com.lexiflow.study.task.mapper.DailyTaskMapper;
import com.lexiflow.wordbook.domain.Word;
import com.lexiflow.wordbook.mapper.WordMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ClozeQuizService {

    private static final int COMPLETED_GROUP_MAX_BLANK_COUNT = 10;
    private static final int MIN_BACKGROUND_WORD_COUNT = 5;
    private static final int MAX_BACKGROUND_WORD_COUNT = 10;
    private static final List<String> DEFINITION_TEXT_FIELDS = List.of("cn", "definition", "definitionZh", "zh", "chinese", "meaning");
    private static final int MAX_GENERATE_ATTEMPTS = 2;
    private static final Duration AI_CACHE_LOCK_TTL = Duration.ofSeconds(130);
    private static final Duration AI_CACHE_LOCK_WAIT_TIMEOUT = Duration.ofSeconds(115);
    private static final Duration AI_CACHE_LOCK_POLL_INTERVAL = Duration.ofMillis(500);

    private final AsyncTaskService asyncTaskService;
    private final AiGatewayService aiGatewayService;
    private final ClozeBlankWordSelector clozeBlankWordSelector;
    private final DailyTaskMapper dailyTaskMapper;
    private final DailyTaskItemMapper dailyTaskItemMapper;
    private final WordMapper wordMapper;
    private final ClozeQuizMapper clozeQuizMapper;
    private final ClozeQuizBlankMapper clozeQuizBlankMapper;
    private final ClozeAttemptMapper clozeAttemptMapper;
    private final ClozeAttemptAnswerMapper clozeAttemptAnswerMapper;
    private final StudyEventMapper studyEventMapper;
    private final WrongWordMapper wrongWordMapper;
    private final ObjectMapper objectMapper;
    private final SpacedRepetitionService spacedRepetitionService;
    private final AiPromptTemplateService aiPromptTemplateService;
    private final AiPromptOutputSchemaService outputSchemaService;
    private final TransactionTemplate transactionTemplate;
    private final RedisAiHitCountBuffer redisAiHitCountBuffer;
    private final RedisDistributedLockService redisDistributedLockService;

    public CreateClozeTaskResponse createClozeTask(Long userId, CreateClozeTaskRequest request) {
        DailyTask dailyTask = getOwnedDailyTask(userId, request.dailyTaskId());
        Long wordbookId = dailyTaskWordbookId(dailyTask);
        ClozeSourceType sourceType = request.safeSourceType();
        String requestJson = toJson(Map.of(
                "dailyTaskId", String.valueOf(request.dailyTaskId()),
                "sourceType", sourceType.name(),
                "targetWordCount", request.safeTargetWordCount(),
                "regenerate", request.safeRegenerate()
        ));
        AsyncTask task = asyncTaskService.createTask(userId, AsyncTaskType.AI_CLOZE, requestJson);
        try {
            asyncTaskService.markRunning(task.getId(), "正在生成完形填空", 20);
            ClozeQuiz quiz = generateQuiz(userId, dailyTask, wordbookId, task.getId(), sourceType, request.safeTargetWordCount(), request.safeRegenerate());
            asyncTaskService.markSuccess(task.getId(), quiz.getId(), "完形填空生成完成");
            return CreateClozeTaskResponse.from(asyncTaskService.getOwnedTaskEntity(userId, task.getId()));
        } catch (BizException ex) {
            asyncTaskService.markFailed(task.getId(), String.valueOf(ex.getErrorCode().getCode()), ex.getCustomMessage());
            throw ex;
        } catch (Exception ex) {
            asyncTaskService.markFailed(task.getId(), String.valueOf(ErrorCode.ASYNC_TASK_FAILED.getCode()), ex.getMessage());
            throw new BizException(ErrorCode.ASYNC_TASK_FAILED, "完形填空生成失败，请稍后重试");
        }
    }

    @Transactional
    public ClozeQuizResponse getQuiz(Long userId, Long quizId) {
        ClozeQuiz quiz = getOwnedQuiz(userId, quizId);
        List<ClozeQuizBlank> blanks = listBlanks(quizId);
        return ClozeQuizResponse.of(
                quiz,
                resolveQuizWordbookId(quiz),
                parseJsonNode(quiz.getCandidateWords()),
                blanks.stream().map(ClozeBlankResponse::from).toList(),
                findQuizAttemptResponse(userId, quizId)
        );
    }

    @Transactional
    public ClozeAttemptResponse submitAttempt(Long userId, Long quizId, SubmitClozeAttemptRequest request) {
        ClozeQuiz quiz = getOwnedQuiz(userId, quizId);
        boolean submitted = clozeAttemptMapper.selectCount(new LambdaQueryWrapper<ClozeAttempt>()
                .eq(ClozeAttempt::getQuizId, quizId)
                .eq(ClozeAttempt::getUserId, userId)) > 0;
        if (submitted) {
            throw new BizException(ErrorCode.CLOZE_ATTEMPT_SUBMITTED);
        }

        List<ClozeQuizBlank> blanks = listBlanks(quizId);
        Map<Long, ClozeQuizBlank> blankMap = blanks.stream().collect(Collectors.toMap(ClozeQuizBlank::getId, Function.identity()));
        Map<Long, Word> wordMap = loadWordMap(blanks);
        Map<Long, String> answerMap = request.answers().stream()
                .collect(Collectors.toMap(SubmitClozeAttemptRequest.AnswerRequest::blankId, answer -> normalizeAnswer(answer.answer()), (left, right) -> right));
        if (answerMap.keySet().stream().anyMatch(blankId -> !blankMap.containsKey(blankId))) {
            throw new BizException(ErrorCode.BAD_REQUEST, "存在不属于当前题目的空格答案");
        }

        int correctCount = 0;
        List<ClozeAttemptAnswer> answerEntities = new ArrayList<>();
        for (ClozeQuizBlank blank : blanks) {
            String userAnswer = answerMap.get(blank.getId());
            boolean correct = normalizeAnswer(blank.getAnswerWord()).equals(userAnswer);
            if (correct) {
                correctCount++;
            }
            ClozeAttemptAnswer answer = new ClozeAttemptAnswer();
            answer.setQuizId(quizId);
            answer.setBlankId(blank.getId());
            answer.setWordId(blank.getWordId());
            answer.setUserAnswer(userAnswer);
            answer.setCorrectAnswer(blank.getAnswerWord());
            answer.setCorrect(correct);
            answer.setDeleted(0);
            answerEntities.add(answer);
        }

        int totalBlanks = blanks.size();
        int wrongCount = totalBlanks - correctCount;
        ClozeAttempt attempt = new ClozeAttempt();
        attempt.setQuizId(quizId);
        attempt.setUserId(userId);
        attempt.setWordbookId(quiz.getWordbookId());
        attempt.setTotalBlanks(totalBlanks);
        attempt.setCorrectCount(correctCount);
        attempt.setWrongCount(wrongCount);
        attempt.setScore(calculateScore(correctCount, totalBlanks));
        attempt.setDurationSeconds(request.durationSeconds());
        attempt.setSubmittedAt(LocalDateTime.now());
        attempt.setDeleted(0);
        clozeAttemptMapper.insert(attempt);

        boolean skipScheduling = isWrongWordPracticeQuiz(quiz);
        for (ClozeAttemptAnswer answer : answerEntities) {
            answer.setAttemptId(attempt.getId());
            clozeAttemptAnswerMapper.insert(answer);
            StudyEvent event = createStudyEvent(userId, quiz, answer, request.durationSeconds(), attempt.getId());
            if (!answer.getCorrect()) {
                upsertWrongWord(userId, quiz.getWordbookId(), answer.getWordId(), event.getId());
                if (!skipScheduling) {
                    spacedRepetitionService.applyFeedback(
                            userId,
                            quiz.getWordbookId(),
                            answer.getWordId(),
                            null,
                            StudyFeedback.UNKNOWN,
                            StudyScene.QUIZ
                    );
                }
            }
        }

        List<ClozeAttemptAnswerResponse> responses = answerEntities.stream()
                .map(answer -> buildAttemptAnswerResponse(answer, blankMap.get(answer.getBlankId()), wordMap.get(answer.getWordId())))
                .toList();
        return ClozeAttemptResponse.of(attempt, responses);
    }

    @Transactional
    public ClozeAttemptResponse getAttempt(Long userId, Long attemptId) {
        ClozeAttempt attempt = clozeAttemptMapper.selectOne(new LambdaQueryWrapper<ClozeAttempt>()
                .eq(ClozeAttempt::getId, attemptId)
                .eq(ClozeAttempt::getUserId, userId)
                .last("LIMIT 1"));
        if (attempt == null) {
            throw new BizException(ErrorCode.CLOZE_QUIZ_NOT_FOUND);
        }
        return buildAttemptResponse(attempt);
    }

    private ClozeAttemptResponse findQuizAttemptResponse(Long userId, Long quizId) {
        ClozeAttempt attempt = clozeAttemptMapper.selectOne(new LambdaQueryWrapper<ClozeAttempt>()
                .eq(ClozeAttempt::getQuizId, quizId)
                .eq(ClozeAttempt::getUserId, userId)
                .last("LIMIT 1"));
        return attempt == null ? null : buildAttemptResponse(attempt);
    }

    private ClozeAttemptResponse buildAttemptResponse(ClozeAttempt attempt) {
        List<ClozeAttemptAnswer> answers = clozeAttemptAnswerMapper.selectList(new LambdaQueryWrapper<ClozeAttemptAnswer>()
                .eq(ClozeAttemptAnswer::getAttemptId, attempt.getId())
                .orderByAsc(ClozeAttemptAnswer::getId));
        Map<Long, ClozeQuizBlank> blankMap = clozeQuizBlankMapper.selectBatchIds(answers.stream().map(ClozeAttemptAnswer::getBlankId).toList())
                .stream()
                .collect(Collectors.toMap(ClozeQuizBlank::getId, Function.identity()));
        Map<Long, Word> wordMap = loadWordMap(blankMap.values().stream().toList());
        return ClozeAttemptResponse.of(attempt, answers.stream()
                .map(answer -> buildAttemptAnswerResponse(answer, blankMap.get(answer.getBlankId()), wordMap.get(answer.getWordId())))
                .toList());
    }

    private ClozeAttemptAnswerResponse buildAttemptAnswerResponse(ClozeAttemptAnswer answer, ClozeQuizBlank blank, Word word) {
        ClozeExplanationDetail explanation = resolveExplanationDetail(blank, word);
        List<ClozeDefinitionGroupResponse> definitions = buildDefinitionGroups(word);
        String usedPos = StringUtils.hasText(explanation.usedPos()) ? explanation.usedPos() : resolvePrimaryPos(word);
        return ClozeAttemptAnswerResponse.of(
                answer,
                blank,
                usedPos,
                explanation.definitionZh(),
                definitions,
                explanation.reasonZh()
        );
    }

    private Map<Long, Word> loadWordMap(List<ClozeQuizBlank> blanks) {
        List<Long> wordIds = blanks.stream()
                .map(ClozeQuizBlank::getWordId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (wordIds.isEmpty()) {
            return Map.of();
        }
        return wordMapper.selectBatchIds(wordIds).stream()
                .collect(Collectors.toMap(Word::getId, Function.identity()));
    }

    protected ClozeQuiz generateQuiz(Long userId, DailyTask dailyTask, Long wordbookId, Long asyncTaskId, ClozeSourceType sourceType, int targetWordCount, boolean regenerate) {
        ClozeWordSelection selection = selectClozeWords(userId, dailyTask, wordbookId, sourceType, targetWordCount);
        if (selection.targetWords().isEmpty() || selection.blankWords().isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "今日任务暂无可用于生成完形填空的目标词");
        }
        ResolvedAiPromptTemplate promptTemplate = aiPromptTemplateService.resolve(AiPromptFeatureType.CLOZE_QUIZ, wordbookId);
        String sourceHash = buildClozeSourceHash(userId, dailyTask, wordbookId, sourceType, selection, promptTemplate);
        if (!regenerate) {
            ClozeQuiz cachedQuiz = tryReuseQuizFromCache(userId, sourceHash);
            if (cachedQuiz != null) {
                return cachedQuiz;
            }
        }

        RedisLockAttempt lockAttempt = tryAcquireClozeLock(sourceHash, regenerate);
        try {
            if (isLockHeld(lockAttempt)) {
                ClozeQuiz cachedQuiz = waitForClozeQuizCache(userId, sourceHash, lockAttempt.lock().key());
                if (cachedQuiz != null) {
                    incrementClozeQuizHit(cachedQuiz);
                    return cachedQuiz;
                }
                lockAttempt = tryAcquireClozeLock(sourceHash, regenerate);
                if (isLockHeld(lockAttempt)) {
                    throw new BizException(ErrorCode.AI_CALL_FAILED, "相同完形填空仍在生成中，请稍后重试");
                }
            }

            if (!regenerate) {
                ClozeQuiz cachedQuiz = tryReuseQuizFromCache(userId, sourceHash);
                if (cachedQuiz != null) {
                    return cachedQuiz;
                }
            }

            BizException lastGenerationError = null;
            List<String> attemptErrors = new ArrayList<>();
            for (int attempt = 1; attempt <= MAX_GENERATE_ATTEMPTS; attempt++) {
                AiPrompt prompt = buildPrompt(sourceType, selection, sourceHash, promptTemplate);
                try {
                    AiChatCompletionResult result = aiGatewayService.generateJson(userId, AiContentType.CLOZE, prompt, asyncTaskId);
                    JsonNode content = parseJson(result.content());
                    validateGeneratedContent(content, selection);
                    return saveGeneratedQuiz(userId, dailyTask, wordbookId, asyncTaskId, sourceType, selection, sourceHash, content);
                } catch (BizException ex) {
                    if (ex.getErrorCode() != ErrorCode.AI_CALL_FAILED) {
                        throw ex;
                    }
                    lastGenerationError = ex;
                    attemptErrors.add("第 " + attempt + " 次：" + generationErrorMessage(ex));
                }
            }
            throw lastGenerationError == null
                    ? new BizException(ErrorCode.AI_CALL_FAILED, "AI 完形填空生成失败，请稍后重试")
                    : new BizException(ErrorCode.AI_CALL_FAILED, buildGenerateFailureMessage(attemptErrors, lastGenerationError));
        } finally {
            releaseClozeLock(lockAttempt);
        }
    }

    private String buildGenerateFailureMessage(List<String> attemptErrors, BizException fallbackError) {
        List<String> messages = attemptErrors.stream()
                .filter(StringUtils::hasText)
                .toList();
        if (messages.isEmpty()) {
            return fallbackError.getCustomMessage();
        }
        return "AI 完形填空生成失败：" + String.join("；", messages);
    }

    private String generationErrorMessage(BizException ex) {
        return StringUtils.hasText(ex.getCustomMessage()) ? ex.getCustomMessage() : ex.getErrorCode().getMessage();
    }

    private ClozeWordSelection selectClozeWords(Long userId, DailyTask dailyTask, Long wordbookId, ClozeSourceType sourceType, int targetWordCount) {
        return switch (sourceType) {
            case COMPLETED_GROUP -> selectCompletedGroupWords(userId, dailyTask, targetWordCount);
            case TODAY_NEW -> selectTodayNewWords(userId, dailyTask, targetWordCount);
            case WRONG_WORDS -> selectWrongWords(userId, dailyTask, wordbookId, targetWordCount);
            case MIXED -> selectMixedWords(userId, dailyTask, wordbookId, targetWordCount);
        };
    }

    private ClozeWordSelection selectTodayNewWords(Long userId, DailyTask dailyTask, int targetWordCount) {
        List<Long> newWordIds = loadDailyTaskWordIds(userId, dailyTask.getId(), DailyTaskItemType.NEW);
        List<Long> reviewWordIds = loadDailyTaskWordIds(userId, dailyTask.getId(), DailyTaskItemType.REVIEW);
        List<Long> blankWordIds = randomSample(newWordIds, targetWordCount);
        appendRandomUnique(blankWordIds, reviewWordIds, targetWordCount);
        if (blankWordIds.size() < targetWordCount) {
            throw new BizException(ErrorCode.BAD_REQUEST, "今日新词和复习词不足，无法生成 " + targetWordCount + " 个空");
        }
        List<Long> backgroundCandidateIds = concatWordIds(newWordIds, reviewWordIds);
        return buildSelection(blankWordIds, backgroundCandidateIds, targetWordCount, "今日新词和复习词不足，无法生成 " + targetWordCount + " 个空");
    }

    private ClozeWordSelection selectWrongWords(Long userId, DailyTask dailyTask, Long wordbookId, int targetWordCount) {
        List<Long> wrongWordIds = loadUnresolvedWrongWordIds(userId, wordbookId);
        if (distinctWordIds(wrongWordIds).size() < targetWordCount) {
            throw new BizException(ErrorCode.BAD_REQUEST, "未解决错词不足，无法生成 " + targetWordCount + " 个空");
        }
        List<Long> blankWordIds = randomSample(wrongWordIds, targetWordCount);
        List<Long> backgroundCandidateIds = concatWordIds(
                wrongWordIds,
                loadDailyTaskWordIds(userId, dailyTask.getId(), DailyTaskItemType.NEW),
                loadDailyTaskWordIds(userId, dailyTask.getId(), DailyTaskItemType.REVIEW)
        );
        return buildSelection(blankWordIds, backgroundCandidateIds, targetWordCount, "未解决错词不足，无法生成 " + targetWordCount + " 个空");
    }

    private ClozeWordSelection selectMixedWords(Long userId, DailyTask dailyTask, Long wordbookId, int targetWordCount) {
        List<Long> newWordIds = loadDailyTaskWordIds(userId, dailyTask.getId(), DailyTaskItemType.NEW);
        List<Long> wrongWordIds = loadUnresolvedWrongWordIds(userId, wordbookId);
        List<Long> reviewWordIds = loadDailyTaskWordIds(userId, dailyTask.getId(), DailyTaskItemType.REVIEW);
        if (distinctWordIds(concatWordIds(newWordIds, wrongWordIds)).size() < targetWordCount) {
            throw new BizException(ErrorCode.BAD_REQUEST, "今日新词和错词不足，无法生成 " + targetWordCount + " 个空");
        }

        int newQuota = (int) Math.ceil(targetWordCount * 0.6d);
        int wrongQuota = targetWordCount - newQuota;
        List<Long> blankWordIds = randomSample(newWordIds, newQuota);
        appendRandomUnique(blankWordIds, wrongWordIds, newQuota + wrongQuota);
        appendRandomUnique(blankWordIds, newWordIds, targetWordCount);
        appendRandomUnique(blankWordIds, wrongWordIds, targetWordCount);
        if (blankWordIds.size() < targetWordCount) {
            throw new BizException(ErrorCode.BAD_REQUEST, "今日新词和错词不足，无法生成 " + targetWordCount + " 个空");
        }

        List<Long> backgroundCandidateIds = concatWordIds(newWordIds, wrongWordIds, reviewWordIds);
        return buildSelection(blankWordIds, backgroundCandidateIds, targetWordCount, "今日新词和错词不足，无法生成 " + targetWordCount + " 个空");
    }

    private List<Long> loadDailyTaskWordIds(Long userId, Long dailyTaskId, DailyTaskItemType itemType) {
        return dailyTaskItemMapper.selectList(new LambdaQueryWrapper<DailyTaskItem>()
                        .eq(DailyTaskItem::getDailyTaskId, dailyTaskId)
                        .eq(DailyTaskItem::getUserId, userId)
                        .eq(DailyTaskItem::getItemType, itemType)
                        .orderByAsc(DailyTaskItem::getSequenceNo)
                        .orderByAsc(DailyTaskItem::getId))
                .stream()
                .map(DailyTaskItem::getWordId)
                .filter(Objects::nonNull)
                .toList();
    }

    private List<Long> loadUnresolvedWrongWordIds(Long userId, Long wordbookId) {
        return wrongWordMapper.selectList(new LambdaQueryWrapper<WrongWord>()
                        .eq(WrongWord::getUserId, userId)
                        .eq(WrongWord::getWordbookId, wordbookId)
                        .eq(WrongWord::getResolved, false)
                        .orderByAsc(WrongWord::getId))
                .stream()
                .map(WrongWord::getWordId)
                .filter(Objects::nonNull)
                .toList();
    }

    private ClozeWordSelection buildSelection(List<Long> blankWordIds, List<Long> backgroundCandidateIds, int targetWordCount, String insufficientMessage) {
        List<Word> blankWords = findWordsKeepingOrder(blankWordIds);
        if (blankWords.size() < targetWordCount) {
            throw new BizException(ErrorCode.BAD_REQUEST, insufficientMessage);
        }
        List<Long> backgroundWordIds = selectBackgroundWordIds(backgroundCandidateIds, blankWordIds);
        List<Word> backgroundWords = findWordsKeepingOrder(backgroundWordIds);
        return new ClozeWordSelection(concatWords(blankWords, backgroundWords), blankWords, backgroundWords);
    }

    private List<Long> selectBackgroundWordIds(List<Long> candidateWordIds, List<Long> blankWordIds) {
        Set<Long> blankIdSet = new LinkedHashSet<>(blankWordIds);
        List<Long> candidates = distinctWordIds(candidateWordIds).stream()
                .filter(wordId -> !blankIdSet.contains(wordId))
                .toList();
        return randomSample(candidates, randomBackgroundLimit(candidates.size()));
    }

    private int randomBackgroundLimit(int candidateCount) {
        if (candidateCount <= MIN_BACKGROUND_WORD_COUNT) {
            return candidateCount;
        }
        int max = Math.min(MAX_BACKGROUND_WORD_COUNT, candidateCount);
        return MIN_BACKGROUND_WORD_COUNT + (int) Math.floor(Math.random() * (max - MIN_BACKGROUND_WORD_COUNT + 1));
    }

    @SafeVarargs
    private final List<Long> concatWordIds(List<Long>... wordIdGroups) {
        List<Long> wordIds = new ArrayList<>();
        for (List<Long> group : wordIdGroups) {
            wordIds.addAll(group);
        }
        return wordIds;
    }

    private List<Word> concatWords(List<Word> first, List<Word> second) {
        List<Word> words = new ArrayList<>(first);
        words.addAll(second);
        return words;
    }

    private List<Long> distinctWordIds(List<Long> wordIds) {
        return wordIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.collectingAndThen(Collectors.toCollection(LinkedHashSet::new), ArrayList::new));
    }

    private List<Long> randomSample(List<Long> wordIds, int limit) {
        if (limit <= 0) {
            return new ArrayList<>();
        }
        List<Long> candidates = distinctWordIds(wordIds);
        Collections.shuffle(candidates);
        return new ArrayList<>(candidates.subList(0, Math.min(limit, candidates.size())));
    }

    private void appendRandomUnique(List<Long> selectedWordIds, List<Long> candidateWordIds, int limit) {
        Set<Long> selected = new LinkedHashSet<>(selectedWordIds);
        for (Long candidateWordId : randomSample(candidateWordIds, candidateWordIds.size())) {
            if (selectedWordIds.size() >= limit) {
                return;
            }
            if (selected.add(candidateWordId)) {
                selectedWordIds.add(candidateWordId);
            }
        }
    }

    private ClozeWordSelection selectCompletedGroupWords(Long userId, DailyTask dailyTask, int targetWordCount) {
        if (dailyTask.getStatus() != DailyTaskStatus.DONE) {
            throw new BizException(ErrorCode.BAD_REQUEST, "完成本组单词后才能生成本组完形填空");
        }
        List<DailyTaskItem> items = dailyTaskItemMapper.selectList(new LambdaQueryWrapper<DailyTaskItem>()
                .eq(DailyTaskItem::getDailyTaskId, dailyTask.getId())
                .eq(DailyTaskItem::getUserId, userId)
                .eq(DailyTaskItem::getStatus, DailyTaskItemStatus.DONE)
                .orderByAsc(DailyTaskItem::getSequenceNo)
                .orderByAsc(DailyTaskItem::getId));
        if (items.isEmpty()) {
            return new ClozeWordSelection(List.of(), List.of());
        }
        List<Long> targetWordIds = items.stream().map(DailyTaskItem::getWordId).distinct().toList();
        Map<Long, DailyTaskItem> itemMap = items.stream()
                .collect(Collectors.toMap(DailyTaskItem::getWordId, Function.identity(), (left, right) -> left, LinkedHashMap::new));
        Map<Long, Word> wordMap = wordMapper.selectBatchIds(targetWordIds).stream()
                .collect(Collectors.toMap(Word::getId, Function.identity()));
        List<Word> targetWords = targetWordIds.stream().map(wordMap::get).filter(Objects::nonNull).toList();
        int blankCount = Math.min(Math.min(COMPLETED_GROUP_MAX_BLANK_COUNT, targetWordCount), targetWords.size());
        if (blankCount <= 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, "本组暂无可用于生成完形填空的单词");
        }
        Map<Long, StudyFeedback> feedbackMap = selectFeedbackMap(userId, dailyTask.getId(), itemMap);
        List<Word> blankWords = clozeBlankWordSelector.selectBlankWords(targetWords, feedbackMap, blankCount, dailyTask.getId());
        return new ClozeWordSelection(targetWords, blankWords);
    }

    private Map<Long, StudyFeedback> selectFeedbackMap(Long userId, Long dailyTaskId, Map<Long, DailyTaskItem> itemMap) {
        if (itemMap.isEmpty()) {
            return Map.of();
        }
        Set<Long> taskItemIds = itemMap.values().stream().map(DailyTaskItem::getId).collect(Collectors.toSet());
        Map<Long, StudyFeedback> feedbackMap = new LinkedHashMap<>();
        studyEventMapper.selectList(new LambdaQueryWrapper<StudyEvent>()
                        .eq(StudyEvent::getUserId, userId)
                        .eq(StudyEvent::getDailyTaskId, dailyTaskId)
                        .in(StudyEvent::getDailyTaskItemId, taskItemIds)
                        .isNotNull(StudyEvent::getFeedback)
                        .orderByAsc(StudyEvent::getCreatedAt)
                        .orderByAsc(StudyEvent::getId))
                .forEach(event -> feedbackMap.merge(event.getWordId(), event.getFeedback(), ClozeBlankWordSelector::higherFeedback));
        itemMap.forEach((wordId, item) -> feedbackMap.putIfAbsent(wordId, ClozeBlankWordSelector.parseFeedback(item.getFeedback())));
        return feedbackMap;
    }

    private List<Word> findWordsKeepingOrder(List<Long> wordIds) {
        if (wordIds.isEmpty()) {
            return List.of();
        }
        Map<Long, Word> wordMap = wordMapper.selectBatchIds(wordIds).stream()
                .collect(Collectors.toMap(Word::getId, Function.identity()));
        return wordIds.stream().map(wordMap::get).filter(Objects::nonNull).toList();
    }

    private ClozeQuiz tryReuseQuizFromCache(Long userId, String sourceHash) {
        ClozeQuiz cachedQuiz = findActiveClozeQuiz(userId, clozeCacheKey(sourceHash));
        if (cachedQuiz == null) {
            return null;
        }
        incrementClozeQuizHit(cachedQuiz);
        return cachedQuiz;
    }

    private ClozeQuiz saveGeneratedQuiz(Long userId, DailyTask dailyTask, Long wordbookId, Long asyncTaskId, ClozeSourceType sourceType, ClozeWordSelection selection, String sourceHash, JsonNode content) {
        return transactionTemplate.execute(status -> {
            String cacheKey = clozeCacheKey(sourceHash);
            deactivateClozeQuizCache(cacheKey);
            try {
                return saveQuiz(userId, dailyTask, wordbookId, asyncTaskId, sourceType, selection, sourceHash, cacheKey, content);
            } catch (DuplicateKeyException ignored) {
                ClozeQuiz active = findActiveClozeQuiz(userId, cacheKey);
                if (active != null) {
                    return active;
                }
                throw ignored;
            }
        });
    }

    private ClozeQuiz findActiveClozeQuiz(Long userId, String cacheKey) {
        return clozeQuizMapper.selectOne(new LambdaQueryWrapper<ClozeQuiz>()
                .eq(ClozeQuiz::getUserId, userId)
                .eq(ClozeQuiz::getCacheKey, cacheKey)
                .eq(ClozeQuiz::getCacheActive, true)
                .eq(ClozeQuiz::getDeleted, 0)
                .last("LIMIT 1"));
    }

    private RedisLockAttempt tryAcquireClozeLock(String sourceHash, boolean regenerate) {
        String cacheKey = clozeCacheKey(sourceHash);
        if (regenerate) {
            return RedisLockAttempt.unavailable(RedisKeys.aiLockKey(AiContentType.CLOZE, cacheKey));
        }
        return redisDistributedLockService.tryLock(RedisKeys.aiLockKey(AiContentType.CLOZE, cacheKey), AI_CACHE_LOCK_TTL);
    }

    private boolean isLockHeld(RedisLockAttempt lockAttempt) {
        return lockAttempt != null && !lockAttempt.acquired() && !lockAttempt.unavailable();
    }

    private void releaseClozeLock(RedisLockAttempt lockAttempt) {
        if (lockAttempt != null && lockAttempt.acquired()) {
            redisDistributedLockService.release(lockAttempt.lock());
        }
    }

    private ClozeQuiz waitForClozeQuizCache(Long userId, String sourceHash, String lockKey) {
        String cacheKey = clozeCacheKey(sourceHash);
        long deadline = System.nanoTime() + AI_CACHE_LOCK_WAIT_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            sleepForCachePoll();
            ClozeQuiz cachedQuiz = findActiveClozeQuiz(userId, cacheKey);
            if (cachedQuiz != null) {
                return cachedQuiz;
            }
            if (!redisDistributedLockService.isLocked(lockKey)) {
                break;
            }
        }
        return findActiveClozeQuiz(userId, cacheKey);
    }

    private void sleepForCachePoll() {
        try {
            Thread.sleep(AI_CACHE_LOCK_POLL_INTERVAL.toMillis());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BizException(ErrorCode.AI_CALL_FAILED, "等待相同完形填空生成时被中断");
        }
    }

    private void incrementClozeQuizHit(ClozeQuiz quiz) {
        if (quiz == null || quiz.getId() == null) {
            return;
        }
        quiz.setHitCount((quiz.getHitCount() == null ? 0 : quiz.getHitCount()) + 1);
        if (redisAiHitCountBuffer.incrementHit(AiContentType.CLOZE, quiz.getId())) {
            return;
        }
        transactionTemplate.executeWithoutResult(status -> {
            clozeQuizMapper.updateById(quiz);
        });
    }

    private void deactivateClozeQuizCache(String cacheKey) {
        clozeQuizMapper.update(null, new LambdaUpdateWrapper<ClozeQuiz>()
                .set(ClozeQuiz::getCacheActive, null)
                .eq(ClozeQuiz::getCacheKey, cacheKey)
                .eq(ClozeQuiz::getCacheActive, true)
                .eq(ClozeQuiz::getDeleted, 0));
    }

    private String buildClozeSourceHash(Long userId, DailyTask dailyTask, Long wordbookId, ClozeSourceType sourceType, ClozeWordSelection selection, ResolvedAiPromptTemplate promptTemplate) {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("generatorVersion", "programmatic-blank-v1");
        source.put("userId", String.valueOf(userId));
        source.put("dailyTaskId", String.valueOf(dailyTask.getId()));
        source.put("wordbookId", String.valueOf(wordbookId));
        source.put("sourceType", sourceType.name());
        source.put("targetWordIds", selection.targetWords().stream().map(Word::getId).map(String::valueOf).toList());
        source.put("blankWordIds", selection.blankWords().stream().map(Word::getId).map(String::valueOf).toList());
        source.put("promptFingerprint", promptTemplate.cacheFingerprint());
        return sha256(toJson(source));
    }

    private String clozeCacheKey(String sourceHash) {
        return "cloze:" + sourceHash;
    }

    private ClozeQuiz saveQuiz(Long userId, DailyTask dailyTask, Long wordbookId, Long asyncTaskId, ClozeSourceType sourceType, ClozeWordSelection selection, String sourceHash, String cacheKey, JsonNode content) {
        ProgrammaticClozeDraft draft = buildProgrammaticClozeDraft(content, selection);

        ClozeQuiz quiz = new ClozeQuiz();
        quiz.setUserId(userId);
        quiz.setWordbookId(wordbookId);
        quiz.setDailyTaskId(dailyTask.getId());
        quiz.setAsyncTaskId(asyncTaskId);
        quiz.setSourceType(sourceType);
        quiz.setSourceHash(sourceHash);
        quiz.setCacheKey(cacheKey);
        quiz.setCacheActive(true);
        quiz.setHitCount(0);
        quiz.setTitle(content.path("title").asText("LexiFlow Cloze Practice"));
        quiz.setPassage(draft.passage());
        quiz.setCandidateWords(toJson(normalizeCandidateWordsFromBlanks(draft.blanks())));
        quiz.setTargetWordIds(toJson(selection.targetWords().stream().map(word -> String.valueOf(word.getId())).toList()));
        quiz.setExplanation(content.path("passageZh").asText(null));
        quiz.setDeleted(0);
        clozeQuizMapper.insert(quiz);
        for (ClozeQuizBlank blank : draft.blanks()) {
            blank.setQuizId(quiz.getId());
            clozeQuizBlankMapper.insert(blank);
        }
        return quiz;
    }

    private AiPrompt buildPrompt(ClozeSourceType sourceType, ClozeWordSelection selection, String sourceHash, ResolvedAiPromptTemplate promptTemplate) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("sourceType", sourceType.name());
        context.put("blankWords", toPromptWords(selection.blankWords()));
        context.put("backgroundWords", toPromptWords(selection.backgroundWords()));
        context.put("blankCount", selection.blankWords().size());
        String sourceJson = toJson(context);
        String userPrompt = "本次出题上下文如下：\n"
                + "- blankWords：必须以原词或常见词形变化出现在 passage 中；explanations 里必须为每个 word 返回 usedForm，未变形时 usedForm 等于原词。\n"
                + "- backgroundWords：背景词，可以自然融入短文，不强制全部使用。\n"
                + "- blankCount：目标挖空数量。\n\n"
                + sourceJson
                + "\n\n请基于上述上下文生成完形填空原文。\n\n"
                + promptTemplate.instructionPrompt()
                + "\n\n"
                + outputSchemaService.buildOutputFormatPrompt(promptTemplate.outputSchemaJson());
        return new AiPrompt(
                promptTemplate.systemPrompt(),
                userPrompt,
                sourceHash,
                promptTemplate.featureType().name(),
                promptTemplate.templateId(),
                promptTemplate.templateName()
        );
    }

    private List<Map<String, Object>> toPromptWords(List<Word> words) {
        return words.stream()
                .map(word -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("word", word.getWord());
                    item.put("primaryPos", safe(word.getPrimaryPos()));
                    item.put("primaryDefinition", safe(word.getPrimaryDefinition()));
                    item.put("definitions", buildPromptDefinitions(word));
                    return item;
                })
                .toList();
    }

    private List<Map<String, Object>> buildPromptDefinitions(Word word) {
        List<DefinitionGroup> groups = parseDefinitionGroups(word);
        if (groups.isEmpty()) {
            return List.of();
        }
        return groups.stream()
                .map(group -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("pos", group.pos());
                    item.put("definitions", group.definitions());
                    return item;
                })
                .toList();
    }

    private void validateGeneratedContent(JsonNode content, ClozeWordSelection selection) {
        String passageZh = content.path("passageZh").asText("");
        if (!StringUtils.hasText(passageZh) || !containsCjk(passageZh)) {
            throw new BizException(ErrorCode.AI_CALL_FAILED, "AI 完形填空缺少短文中文翻译");
        }
        buildProgrammaticClozeDraft(content, selection);
    }

    private ProgrammaticClozeDraft buildProgrammaticClozeDraft(JsonNode content, ClozeWordSelection selection) {
        String passage = content.path("passage").asText("");
        validateGeneratedPassageText(passage);

        Map<String, ClozeExplanationDetail> explanationMap = buildExplanationMap(content);
        Set<String> normalizedBlankWords = new LinkedHashSet<>();
        Set<String> normalizedAnswerWords = new LinkedHashSet<>();
        List<WordOccurrence> occurrences = new ArrayList<>();
        for (Word word : selection.blankWords()) {
            String normalized = normalizeAnswer(word.getWord());
            if (!StringUtils.hasText(normalized) || !normalizedBlankWords.add(normalized)) {
                throw new BizException(ErrorCode.AI_CALL_FAILED, "AI 完形填空挖空词重复或为空");
            }
            ClozeExplanationDetail explanation = explanationMap.get(normalized);
            String usedForm = resolveUsedForm(word, explanation);
            validateUsedForm(word, usedForm);
            List<WordOccurrence> matches = findWordOccurrences(passage, word, usedForm);
            if (matches.isEmpty()) {
                throw new BizException(ErrorCode.AI_CALL_FAILED, "AI 完形填空文章未包含 usedForm：" + word.getWord() + " -> " + usedForm);
            }
            WordOccurrence occurrence = matches.get(0);
            String normalizedAnswer = normalizeAnswer(occurrence.answerWord());
            if (!StringUtils.hasText(normalizedAnswer) || !normalizedAnswerWords.add(normalizedAnswer)) {
                throw new BizException(ErrorCode.AI_CALL_FAILED, "AI 完形填空挖空答案重复或为空：" + occurrence.answerWord());
            }
            occurrences.add(occurrence);
        }

        occurrences.sort((left, right) -> Integer.compare(left.start(), right.start()));
        StringBuilder maskedPassage = new StringBuilder();
        List<ClozeQuizBlank> blanks = new ArrayList<>();
        int cursor = 0;
        int blankNo = 1;
        for (WordOccurrence occurrence : occurrences) {
            if (occurrence.start() < cursor) {
                throw new BizException(ErrorCode.AI_CALL_FAILED, "AI 完形填空挖空词位置重叠");
            }
            maskedPassage.append(passage, cursor, occurrence.start());
            maskedPassage.append("___").append(blankNo).append("___");
            cursor = occurrence.end();

            Word word = occurrence.word();
            ClozeQuizBlank blank = new ClozeQuizBlank();
            blank.setBlankNo(blankNo);
            blank.setWordId(word.getId());
            blank.setAnswerWord(occurrence.answerWord());
            blank.setHint(null);
            ClozeExplanationDetail explanation = explanationMap.get(normalizeAnswer(word.getWord()));
            explanation = explanation == null ? fallbackExplanationDetail(word) : explanation;
            blank.setExplanation(toJson(explanation));
            blank.setDeleted(0);
            blanks.add(blank);
            blankNo++;
        }
        maskedPassage.append(passage.substring(cursor));
        return new ProgrammaticClozeDraft(maskedPassage.toString(), blanks);
    }

    private String resolveUsedForm(Word word, ClozeExplanationDetail explanation) {
        if (explanation != null && StringUtils.hasText(explanation.usedForm())) {
            return explanation.usedForm().trim();
        }
        return word == null ? "" : safe(word.getWord()).trim();
    }

    private void validateUsedForm(Word word, String usedForm) {
        if (!StringUtils.hasText(usedForm)) {
            throw new BizException(ErrorCode.AI_CALL_FAILED, "AI 完形填空 usedForm 为空：" + (word == null ? "" : word.getWord()));
        }
        if (containsCjk(usedForm)) {
            throw new BizException(ErrorCode.AI_CALL_FAILED, "AI 完形填空 usedForm 包含中文：" + usedForm);
        }
        if (Pattern.compile("\\s").matcher(usedForm).find()) {
            throw new BizException(ErrorCode.AI_CALL_FAILED, "AI 完形填空 usedForm 只能是单个词：" + usedForm);
        }
    }

    private void validateGeneratedPassageText(String passage) {
        if (!StringUtils.hasText(passage)) {
            throw new BizException(ErrorCode.AI_CALL_FAILED, "AI 完形填空缺少文章内容");
        }
        String normalizedPassage = passage.toLowerCase(Locale.ROOT);
        if (normalizedPassage.contains("because it relates to")) {
            throw new BizException(ErrorCode.AI_CALL_FAILED, "AI 完形填空文章包含泄题模板");
        }
        if (Pattern.compile("_{2,}\\s*\\d+\\s*_{2,}").matcher(passage).find()) {
            throw new BizException(ErrorCode.AI_CALL_FAILED, "AI 完形填空文章不应提前挖空");
        }
        if (containsCjk(passage)) {
            throw new BizException(ErrorCode.AI_CALL_FAILED, "AI 完形填空文章包含中文释义");
        }
    }

    private List<WordOccurrence> findWordOccurrences(String text, Word word, String usedForm) {
        if (!StringUtils.hasText(text) || word == null || !StringUtils.hasText(usedForm)) {
            return List.of();
        }
        Matcher matcher = wordPattern(usedForm).matcher(text);
        List<WordOccurrence> occurrences = new ArrayList<>();
        while (matcher.find()) {
            occurrences.add(new WordOccurrence(word, text.substring(matcher.start(), matcher.end()), matcher.start(), matcher.end()));
        }
        return occurrences;
    }

    private Pattern wordPattern(String word) {
        return Pattern.compile("(?i)(?<![A-Za-z])" + Pattern.quote(word.trim()) + "(?![A-Za-z])");
    }

    private boolean containsCjk(String text) {
        return StringUtils.hasText(text) && Pattern.compile("[\\p{IsHan}]").matcher(text).find();
    }

    private Map<String, ClozeExplanationDetail> buildExplanationMap(JsonNode content) {
        Map<String, ClozeExplanationDetail> explanations = new LinkedHashMap<>();
        JsonNode explanationsNode = content.path("explanations");
        if (!explanationsNode.isArray()) {
            return explanations;
        }
        for (JsonNode explanationNode : explanationsNode) {
            String word = explanationNode.path("word").asText("");
            String usedForm = explanationNode.path("usedForm").asText("");
            String usedPos = explanationNode.path("usedPos").asText("");
            String definitionZh = explanationNode.path("definitionZh").asText("");
            String reasonZh = explanationNode.path("reasonZh").asText("");
            if (StringUtils.hasText(word)) {
                explanations.put(normalizeAnswer(word), new ClozeExplanationDetail(usedForm, usedPos, definitionZh, reasonZh));
            }
        }
        return explanations;
    }

    private List<String> normalizeCandidateWordsFromBlanks(List<ClozeQuizBlank> blanks) {
        LinkedHashSet<String> words = new LinkedHashSet<>();
        blanks.stream().map(ClozeQuizBlank::getAnswerWord).filter(StringUtils::hasText).forEach(words::add);
        return words.stream().toList();
    }

    private ClozeExplanationDetail fallbackExplanationDetail(Word word) {
        String definitionZh = resolveChineseDefinition(word);
        String usedPos = resolvePrimaryPos(word);
        String reasonZh;
        if (StringUtils.hasText(definitionZh)) {
            reasonZh = "这里语义上需要这个词，对应中文释义是“" + definitionZh + "”。";
        } else if (word != null && StringUtils.hasText(word.getWord())) {
            reasonZh = "这里语义上需要本组目标词 " + word.getWord() + "。";
        } else {
            reasonZh = "这里语义上需要本组目标词。";
        }
        return new ClozeExplanationDetail(word == null ? "" : safe(word.getWord()), usedPos, definitionZh, reasonZh);
    }

    private ClozeExplanationDetail resolveExplanationDetail(ClozeQuizBlank blank, Word word) {
        String rawExplanation = blank == null ? "" : safe(blank.getExplanation());
        String usedForm = blank == null ? "" : safe(blank.getAnswerWord());
        String definitionZh = resolveChineseDefinition(word);
        String usedPos = resolvePrimaryPos(word);
        String reasonZh = "";

        JsonNode node = readJsonNodeOrNull(rawExplanation);
        if (node != null && node.isObject()) {
            String parsedUsedForm = node.path("usedForm").asText("");
            String parsedUsedPos = node.path("usedPos").asText("");
            String parsedDefinition = node.path("definitionZh").asText("");
            String parsedReason = node.path("reasonZh").asText("");
            if (StringUtils.hasText(parsedUsedForm)) {
                usedForm = parsedUsedForm;
            }
            if (StringUtils.hasText(parsedUsedPos)) {
                usedPos = parsedUsedPos;
            }
            if (StringUtils.hasText(parsedDefinition)) {
                definitionZh = parsedDefinition;
            }
            if (StringUtils.hasText(parsedReason)) {
                reasonZh = parsedReason;
            }
        }

        if (!StringUtils.hasText(reasonZh)) {
            reasonZh = fallbackExplanationDetail(word).reasonZh();
        }
        return new ClozeExplanationDetail(usedForm, usedPos, definitionZh, reasonZh);
    }

    private JsonNode readJsonNodeOrNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String text = value.trim();
        if (!text.startsWith("{") && !text.startsWith("[")) {
            return null;
        }
        try {
            return objectMapper.readTree(text);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String resolveChineseDefinition(Word word) {
        if (word == null) {
            return "";
        }
        String definition = safe(word.getPrimaryDefinition());
        if (StringUtils.hasText(definition)) {
            return definition;
        }
        return parseDefinitionGroups(word).stream()
                .flatMap(group -> group.definitions().stream())
                .findFirst()
                .orElse("");
    }

    private String resolvePrimaryPos(Word word) {
        if (word == null) {
            return "";
        }
        String primaryPos = safe(word.getPrimaryPos());
        if (StringUtils.hasText(primaryPos)) {
            return primaryPos;
        }
        return parseDefinitionGroups(word).stream()
                .map(DefinitionGroup::pos)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse("");
    }

    private List<ClozeDefinitionGroupResponse> buildDefinitionGroups(Word word) {
        return parseDefinitionGroups(word).stream()
                .map(group -> new ClozeDefinitionGroupResponse(group.pos(), group.definitions()))
                .toList();
    }

    private List<DefinitionGroup> parseDefinitionGroups(Word word) {
        if (word == null) {
            return List.of();
        }
        List<DefinitionGroup> groups = parseDefinitionGroups(word.getTrans());
        if (groups.isEmpty() && StringUtils.hasText(word.getPrimaryDefinition())) {
            return List.of(new DefinitionGroup(safe(word.getPrimaryPos()), List.of(word.getPrimaryDefinition().trim())));
        }
        return groups;
    }

    private List<DefinitionGroup> parseDefinitionGroups(String transJson) {
        if (!StringUtils.hasText(transJson)) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(transJson);
            LinkedHashMap<String, LinkedHashSet<String>> grouped = new LinkedHashMap<>();
            collectDefinitionGroups(root, "", grouped);
            return grouped.entrySet().stream()
                    .map(entry -> new DefinitionGroup(entry.getKey(), entry.getValue().stream().toList()))
                    .filter(group -> !group.definitions().isEmpty())
                    .toList();
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private void collectDefinitionGroups(JsonNode node, String inheritedPos, LinkedHashMap<String, LinkedHashSet<String>> grouped) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }
        if (node.isArray()) {
            node.forEach(item -> collectDefinitionGroups(item, inheritedPos, grouped));
            return;
        }
        if (node.isObject()) {
            String pos = StringUtils.hasText(node.path("pos").asText("")) ? node.path("pos").asText("").trim() : inheritedPos;
            List<String> definitions = new ArrayList<>();
            for (String field : DEFINITION_TEXT_FIELDS) {
                definitions.addAll(collectDefinitionTexts(node.path(field)));
            }
            definitions.addAll(collectDefinitionTexts(node.path("definitions")));
            definitions.stream()
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .forEach(definition -> grouped.computeIfAbsent(pos, ignored -> new LinkedHashSet<>()).add(definition));
            if (node.has("trans")) {
                collectDefinitionGroups(node.path("trans"), pos, grouped);
            }
            return;
        }
        String text = node.asText("").trim();
        if (StringUtils.hasText(text)) {
            grouped.computeIfAbsent(inheritedPos, ignored -> new LinkedHashSet<>()).add(text);
        }
    }

    private List<String> collectDefinitionTexts(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return List.of();
        }
        if (node.isArray()) {
            List<String> values = new ArrayList<>();
            node.forEach(item -> values.addAll(collectDefinitionTexts(item)));
            return values;
        }
        if (node.isObject()) {
            List<String> values = new ArrayList<>();
            for (String field : DEFINITION_TEXT_FIELDS) {
                values.addAll(collectDefinitionTexts(node.path(field)));
            }
            return values;
        }
        String text = node.asText("").trim();
        return StringUtils.hasText(text) ? List.of(text) : List.of();
    }

    private DailyTask getOwnedDailyTask(Long userId, Long dailyTaskId) {
        DailyTask task = dailyTaskMapper.selectOne(new LambdaQueryWrapper<DailyTask>()
                .eq(DailyTask::getId, dailyTaskId)
                .eq(DailyTask::getUserId, userId)
                .last("LIMIT 1"));
        if (task == null) {
            throw new BizException(ErrorCode.TODAY_TASK_NOT_FOUND);
        }
        return task;
    }

    private ClozeQuiz getOwnedQuiz(Long userId, Long quizId) {
        ClozeQuiz quiz = clozeQuizMapper.selectOne(new LambdaQueryWrapper<ClozeQuiz>()
                .eq(ClozeQuiz::getId, quizId)
                .eq(ClozeQuiz::getUserId, userId)
                .last("LIMIT 1"));
        if (quiz == null) {
            throw new BizException(ErrorCode.CLOZE_QUIZ_NOT_FOUND);
        }
        return quiz;
    }

    private boolean isWrongWordPracticeQuiz(ClozeQuiz quiz) {
        if (quiz.getDailyTaskId() == null) {
            return false;
        }
        DailyTask task = dailyTaskMapper.selectById(quiz.getDailyTaskId());
        return task != null && task.getTaskType() == DailyTaskType.WRONG_WORD_PRACTICE;
    }

    private List<ClozeQuizBlank> listBlanks(Long quizId) {
        return clozeQuizBlankMapper.selectList(new LambdaQueryWrapper<ClozeQuizBlank>()
                .eq(ClozeQuizBlank::getQuizId, quizId)
                .orderByAsc(ClozeQuizBlank::getBlankNo)
                .orderByAsc(ClozeQuizBlank::getId));
    }

    private Long dailyTaskWordbookId(DailyTask dailyTask) {
        DailyTaskItem item = dailyTaskItemMapper.selectOne(new LambdaQueryWrapper<DailyTaskItem>()
                .eq(DailyTaskItem::getDailyTaskId, dailyTask.getId())
                .last("LIMIT 1"));
        if (item == null) {
            throw new BizException(ErrorCode.TODAY_TASK_NOT_FOUND);
        }
        return item.getWordbookId();
    }

    private Long resolveQuizWordbookId(ClozeQuiz quiz) {
        if (quiz.getWordbookId() != null || quiz.getDailyTaskId() == null) {
            return quiz.getWordbookId();
        }
        DailyTaskItem item = dailyTaskItemMapper.selectOne(new LambdaQueryWrapper<DailyTaskItem>()
                .eq(DailyTaskItem::getDailyTaskId, quiz.getDailyTaskId())
                .eq(DailyTaskItem::getUserId, quiz.getUserId())
                .last("LIMIT 1"));
        return item == null ? null : item.getWordbookId();
    }

    private StudyEvent createStudyEvent(Long userId, ClozeQuiz quiz, ClozeAttemptAnswer answer, Integer durationSeconds, Long attemptId) {
        StudyEvent event = new StudyEvent();
        event.setUserId(userId);
        event.setPlanId(null);
        event.setWordbookId(quiz.getWordbookId());
        event.setWordId(answer.getWordId());
        event.setDailyTaskId(quiz.getDailyTaskId());
        event.setDailyTaskItemId(null);
        event.setScene(StudyScene.QUIZ);
        event.setFeedback(null);
        event.setQualityScore(spacedRepetitionService.qualityScore(answer.getCorrect() ? StudyFeedback.KNOWN : StudyFeedback.UNKNOWN));
        event.setIsCorrect(answer.getCorrect());
        event.setDurationSeconds(durationSeconds);
        event.setSourceRefId(attemptId);
        studyEventMapper.insert(event);
        return event;
    }

    private void upsertWrongWord(Long userId, Long wordbookId, Long wordId, Long eventId) {
        WrongWord wrongWord = wrongWordMapper.selectOne(new LambdaQueryWrapper<WrongWord>()
                .eq(WrongWord::getUserId, userId)
                .eq(WrongWord::getWordbookId, wordbookId)
                .eq(WrongWord::getWordId, wordId)
                .last("LIMIT 1"));
        if (wrongWord == null) {
            wrongWord = new WrongWord();
            wrongWord.setUserId(userId);
            wrongWord.setWordbookId(wordbookId);
            wrongWord.setWordId(wordId);
            wrongWord.setWrongCount(1);
            wrongWord.setLastSource(StudyScene.QUIZ);
            wrongWord.setLastEventId(eventId);
            wrongWord.setLastWrongAt(LocalDateTime.now());
            wrongWord.setResolved(false);
            wrongWord.setDeleted(0);
            wrongWordMapper.insert(wrongWord);
            return;
        }
        wrongWord.setWrongCount(wrongWord.getWrongCount() + 1);
        wrongWord.setLastSource(StudyScene.QUIZ);
        wrongWord.setLastEventId(eventId);
        wrongWord.setLastWrongAt(LocalDateTime.now());
        wrongWord.setResolved(false);
        wrongWord.setResolvedAt(null);
        wrongWordMapper.updateById(wrongWord);
    }

    private BigDecimal calculateScore(int correctCount, int totalBlanks) {
        if (totalBlanks <= 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return BigDecimal.valueOf(correctCount)
                .multiply(new BigDecimal("100"))
                .divide(BigDecimal.valueOf(totalBlanks), 2, RoundingMode.HALF_UP);
    }


    private JsonNode parseJsonNode(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception ex) {
            throw new BizException(ErrorCode.INTERNAL_ERROR);
        }
    }
    private JsonNode parseJson(String json) {
        return AiJsonUtils.parseObject(objectMapper, json);
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
            StringBuilder builder = new StringBuilder();
            for (byte b : digest) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (Exception ex) {
            throw new BizException(ErrorCode.INTERNAL_ERROR);
        }
    }

    private String normalizeAnswer(String answer) {
        return answer == null ? "" : answer.trim().toLowerCase(Locale.ROOT);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private record ProgrammaticClozeDraft(String passage, List<ClozeQuizBlank> blanks) {
    }

    private record WordOccurrence(Word word, String answerWord, int start, int end) {
    }

    private record ClozeExplanationDetail(String usedForm, String usedPos, String definitionZh, String reasonZh) {
    }

    private record DefinitionGroup(String pos, List<String> definitions) {
    }

    private record ClozeWordSelection(List<Word> targetWords, List<Word> blankWords, List<Word> backgroundWords) {
        private ClozeWordSelection(List<Word> targetWords, List<Word> blankWords) {
            this(targetWords, blankWords, defaultBackgroundWords(targetWords, blankWords));
        }

        private static List<Word> defaultBackgroundWords(List<Word> targetWords, List<Word> blankWords) {
            Set<Long> blankWordIds = blankWords.stream().map(Word::getId).collect(Collectors.toSet());
            return targetWords.stream().filter(word -> !blankWordIds.contains(word.getId())).toList();
        }
    }

}
