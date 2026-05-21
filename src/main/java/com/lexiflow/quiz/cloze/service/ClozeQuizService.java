package com.lexiflow.quiz.cloze.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lexiflow.ai.content.domain.AiContentCache;
import com.lexiflow.ai.content.domain.AiContentType;
import com.lexiflow.ai.content.mapper.AiContentCacheMapper;
import com.lexiflow.ai.core.dto.AiChatCompletionResult;
import com.lexiflow.ai.core.dto.AiPrompt;
import com.lexiflow.ai.core.service.AiGatewayService;
import com.lexiflow.ai.core.util.AiJsonUtils;
import com.lexiflow.async.domain.AsyncTask;
import com.lexiflow.async.domain.AsyncTaskType;
import com.lexiflow.async.service.AsyncTaskService;
import com.lexiflow.common.error.ErrorCode;
import com.lexiflow.common.exception.BizException;
import com.lexiflow.quiz.cloze.domain.ClozeAttempt;
import com.lexiflow.quiz.cloze.domain.ClozeAttemptAnswer;
import com.lexiflow.quiz.cloze.domain.ClozeQuiz;
import com.lexiflow.quiz.cloze.domain.ClozeQuizBlank;
import com.lexiflow.quiz.cloze.domain.ClozeSourceType;
import com.lexiflow.quiz.cloze.dto.ClozeAttemptAnswerResponse;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ClozeQuizService {

    private static final String SYSTEM_PROMPT = "你是 LexiFlow 的 AI 英语测验出题助手。请只输出合法 JSON，不要输出 Markdown、解释性前后缀或代码块。题目面向备考大学生，短文自然连贯；后端程序会自动挖空、生成候选词和判分。";
    private static final int COMPLETED_GROUP_MAX_BLANK_COUNT = 10;
    private static final int MAX_GENERATE_ATTEMPTS = 2;

    private final AsyncTaskService asyncTaskService;
    private final AiGatewayService aiGatewayService;
    private final AiContentCacheMapper aiContentCacheMapper;
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

    public CreateClozeTaskResponse createClozeTask(Long userId, CreateClozeTaskRequest request) {
        DailyTask dailyTask = getOwnedDailyTask(userId, request.dailyTaskId());
        Long wordbookId = dailyTaskWordbookId(dailyTask);
        ClozeSourceType sourceType = request.safeSourceType();
        String requestJson = toJson(Map.of(
                "dailyTaskId", String.valueOf(request.dailyTaskId()),
                "sourceType", sourceType.name(),
                "targetWordCount", request.safeTargetWordCount()
        ));
        AsyncTask task = asyncTaskService.createTask(userId, AsyncTaskType.AI_CLOZE, requestJson);
        try {
            asyncTaskService.markRunning(task.getId(), "正在生成完形填空", 20);
            ClozeQuiz quiz = generateQuiz(userId, dailyTask, wordbookId, task.getId(), sourceType, request.safeTargetWordCount());
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
                parseJsonNode(quiz.getCandidateWords()),
                blanks.stream().map(ClozeBlankResponse::from).toList()
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
                .map(answer -> ClozeAttemptAnswerResponse.of(answer, blankMap.get(answer.getBlankId())))
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
        List<ClozeAttemptAnswer> answers = clozeAttemptAnswerMapper.selectList(new LambdaQueryWrapper<ClozeAttemptAnswer>()
                .eq(ClozeAttemptAnswer::getAttemptId, attemptId)
                .orderByAsc(ClozeAttemptAnswer::getId));
        Map<Long, ClozeQuizBlank> blankMap = clozeQuizBlankMapper.selectBatchIds(answers.stream().map(ClozeAttemptAnswer::getBlankId).toList())
                .stream()
                .collect(Collectors.toMap(ClozeQuizBlank::getId, Function.identity()));
        return ClozeAttemptResponse.of(attempt, answers.stream()
                .map(answer -> ClozeAttemptAnswerResponse.of(answer, blankMap.get(answer.getBlankId())))
                .toList());
    }

    @Transactional
    protected ClozeQuiz generateQuiz(Long userId, DailyTask dailyTask, Long wordbookId, Long asyncTaskId, ClozeSourceType sourceType, int targetWordCount) {
        ClozeWordSelection selection = selectClozeWords(userId, dailyTask, wordbookId, sourceType, targetWordCount);
        if (selection.targetWords().isEmpty() || selection.blankWords().isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "今日任务暂无可用于生成完形填空的目标词");
        }
        String sourceHash = buildClozeSourceHash(userId, dailyTask, wordbookId, sourceType, selection);
        ClozeQuiz cachedQuiz = tryCreateQuizFromCache(userId, dailyTask, wordbookId, asyncTaskId, sourceType, selection, sourceHash);
        if (cachedQuiz != null) {
            return cachedQuiz;
        }

        BizException lastValidationError = null;
        for (int attempt = 1; attempt <= MAX_GENERATE_ATTEMPTS; attempt++) {
            AiPrompt prompt = buildPrompt(dailyTask, wordbookId, sourceType, selection, attempt, lastValidationError == null ? null : lastValidationError.getCustomMessage());
            try {
                AiChatCompletionResult result = aiGatewayService.generateJson(userId, AiContentType.CLOZE, prompt);
                JsonNode content = parseJson(result.content());
                validateGeneratedContent(content, selection);
                upsertClozeCache(userId, wordbookId, sourceHash, content);
                return saveQuiz(userId, dailyTask, wordbookId, asyncTaskId, sourceType, selection, content);
            } catch (BizException ex) {
                if (ex.getErrorCode() != ErrorCode.AI_CALL_FAILED) {
                    throw ex;
                }
                lastValidationError = ex;
            }
        }
        JsonNode fallbackContent = buildFallbackContent(selection, lastValidationError);
        validateGeneratedContent(fallbackContent, selection);
        return saveQuiz(userId, dailyTask, wordbookId, asyncTaskId, sourceType, selection, fallbackContent);
    }

    private ClozeWordSelection selectClozeWords(Long userId, DailyTask dailyTask, Long wordbookId, ClozeSourceType sourceType, int targetWordCount) {
        if (sourceType == ClozeSourceType.COMPLETED_GROUP) {
            return selectCompletedGroupWords(userId, dailyTask, targetWordCount);
        }
        LinkedHashSet<Long> wordIds = new LinkedHashSet<>();
        if (sourceType == ClozeSourceType.TODAY_NEW || sourceType == ClozeSourceType.MIXED) {
            dailyTaskItemMapper.selectList(new LambdaQueryWrapper<DailyTaskItem>()
                            .eq(DailyTaskItem::getDailyTaskId, dailyTask.getId())
                            .eq(DailyTaskItem::getItemType, DailyTaskItemType.NEW)
                            .orderByAsc(DailyTaskItem::getSequenceNo)
                            .orderByAsc(DailyTaskItem::getId))
                    .stream()
                    .map(DailyTaskItem::getWordId)
                    .forEach(wordIds::add);
        }
        if (sourceType == ClozeSourceType.WRONG_WORDS || sourceType == ClozeSourceType.MIXED) {
            wrongWordMapper.selectList(new LambdaQueryWrapper<WrongWord>()
                            .eq(WrongWord::getUserId, userId)
                            .eq(WrongWord::getWordbookId, wordbookId)
                            .eq(WrongWord::getResolved, false)
                            .orderByDesc(WrongWord::getWrongCount)
                            .orderByDesc(WrongWord::getLastWrongAt))
                    .stream()
                    .map(WrongWord::getWordId)
                    .forEach(wordIds::add);
        }
        List<Long> limitedIds = wordIds.stream().limit(targetWordCount).toList();
        if (limitedIds.isEmpty()) {
            return new ClozeWordSelection(List.of(), List.of());
        }
        List<Word> words = findWordsKeepingOrder(limitedIds);
        return new ClozeWordSelection(words, words);
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

    private ClozeQuiz tryCreateQuizFromCache(
            Long userId,
            DailyTask dailyTask,
            Long wordbookId,
            Long asyncTaskId,
            ClozeSourceType sourceType,
            ClozeWordSelection selection,
            String sourceHash
    ) {
        AiContentCache cache = aiContentCacheMapper.selectOne(new LambdaQueryWrapper<AiContentCache>()
                .eq(AiContentCache::getContentType, AiContentType.CLOZE)
                .eq(AiContentCache::getCacheKey, clozeCacheKey(sourceHash))
                .eq(AiContentCache::getUserId, userId)
                .last("LIMIT 1"));
        if (cache == null) {
            return null;
        }
        try {
            JsonNode content = parseJson(cache.getContentJson());
            validateGeneratedContent(content, selection);
            cache.setHitCount((cache.getHitCount() == null ? 0 : cache.getHitCount()) + 1);
            aiContentCacheMapper.updateById(cache);
            return saveQuiz(userId, dailyTask, wordbookId, asyncTaskId, sourceType, selection, content);
        } catch (BizException ex) {
            return null;
        }
    }

    private void upsertClozeCache(Long userId, Long wordbookId, String sourceHash, JsonNode content) {
        String cacheKey = clozeCacheKey(sourceHash);
        AiContentCache cache = aiContentCacheMapper.selectOne(new LambdaQueryWrapper<AiContentCache>()
                .eq(AiContentCache::getContentType, AiContentType.CLOZE)
                .eq(AiContentCache::getCacheKey, cacheKey)
                .last("LIMIT 1"));
        if (cache == null) {
            cache = new AiContentCache();
            cache.setContentType(AiContentType.CLOZE);
            cache.setCacheKey(cacheKey);
            cache.setUserId(userId);
            cache.setWordbookId(wordbookId);
            cache.setSourceHash(sourceHash);
            cache.setHitCount(0);
            cache.setDeleted(0);
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

    private String buildClozeSourceHash(Long userId, DailyTask dailyTask, Long wordbookId, ClozeSourceType sourceType, ClozeWordSelection selection) {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("generatorVersion", "programmatic-blank-v1");
        source.put("userId", String.valueOf(userId));
        source.put("dailyTaskId", String.valueOf(dailyTask.getId()));
        source.put("wordbookId", String.valueOf(wordbookId));
        source.put("sourceType", sourceType.name());
        source.put("targetWordIds", selection.targetWords().stream().map(Word::getId).map(String::valueOf).toList());
        source.put("blankWordIds", selection.blankWords().stream().map(Word::getId).map(String::valueOf).toList());
        return sha256(toJson(source));
    }

    private String clozeCacheKey(String sourceHash) {
        return "cloze:" + sourceHash;
    }

    private ClozeQuiz saveQuiz(Long userId, DailyTask dailyTask, Long wordbookId, Long asyncTaskId, ClozeSourceType sourceType, ClozeWordSelection selection, JsonNode content) {
        ProgrammaticClozeDraft draft = buildProgrammaticClozeDraft(content, selection);

        ClozeQuiz quiz = new ClozeQuiz();
        quiz.setUserId(userId);
        quiz.setWordbookId(wordbookId);
        quiz.setDailyTaskId(dailyTask.getId());
        quiz.setAsyncTaskId(asyncTaskId);
        quiz.setSourceType(sourceType);
        quiz.setTitle(content.path("title").asText("LexiFlow Cloze Practice"));
        quiz.setPassage(draft.passage());
        quiz.setCandidateWords(toJson(normalizeCandidateWords(selection.blankWords())));
        quiz.setTargetWordIds(toJson(selection.targetWords().stream().map(word -> String.valueOf(word.getId())).toList()));
        quiz.setExplanation(content.path("explanation").asText(null));
        quiz.setDeleted(0);
        clozeQuizMapper.insert(quiz);
        for (ClozeQuizBlank blank : draft.blanks()) {
            blank.setQuizId(quiz.getId());
            clozeQuizBlankMapper.insert(blank);
        }
        return quiz;
    }

    private AiPrompt buildPrompt(DailyTask dailyTask, Long wordbookId, ClozeSourceType sourceType, ClozeWordSelection selection, int attempt, String previousError) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("dailyTaskId", String.valueOf(dailyTask.getId()));
        context.put("sourceType", sourceType.name());
        context.put("wordbookId", String.valueOf(wordbookId));
        context.put("blankWords", toPromptWords(selection.blankWords()));
        context.put("backgroundWords", toPromptWords(selection.backgroundWords()));
        context.put("blankCount", selection.blankWords().size());
        context.put("attempt", attempt);
        if (StringUtils.hasText(previousError)) {
            context.put("previousValidationError", previousError);
        }
        String sourceJson = toJson(context);
        String schema = "输出 JSON 对象：title 字符串；passage 字符串，必须是包含 blankWords 原词的完整英文短文，不要提前挖空；explanations 数组，每项包含 word、explanation；explanation 字符串。";
        String rules = "严格规则：1. passage 必须逐字包含 blankWords 中每个 word，且每个 word 在 passage 中只出现一次；2. 可以参考 definitionZh、pos、examples 理解词义和用法，但 passage 不得出现中文释义、英文释义、词性解释、because it relates to 或类似泄题模板；3. 不要输出 ___1___ 这类占位符，后端会按实际出现位置自动挖空并生成正确答案；4. backgroundWords 是软约束，尽量自然融入 passage，影响通顺时可以省略；5. 不要输出 candidateWords 或 blanks，候选词和答案由后端程序生成；6. passage 要自然连贯，控制在 100-180 个英文词。";
        String userPrompt = schema + "\n" + rules + "\n" + sourceJson;
        return new AiPrompt(SYSTEM_PROMPT, userPrompt, sha256(sourceJson));
    }

    private List<Map<String, Object>> toPromptWords(List<Word> words) {
        return words.stream()
                .map(word -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("wordId", String.valueOf(word.getId()));
                    item.put("word", word.getWord());
                    item.put("pos", safe(word.getPrimaryPos()));
                    item.put("definitionZh", safe(word.getPrimaryDefinition()));
                    item.put("examples", safe(word.getSentences()));
                    return item;
                })
                .toList();
    }

    private void validateGeneratedContent(JsonNode content, ClozeWordSelection selection) {
        buildProgrammaticClozeDraft(content, selection);
    }

    private ProgrammaticClozeDraft buildProgrammaticClozeDraft(JsonNode content, ClozeWordSelection selection) {
        String passage = content.path("passage").asText("");
        validateGeneratedPassageText(passage);

        Set<String> normalizedBlankWords = new LinkedHashSet<>();
        List<WordOccurrence> occurrences = new ArrayList<>();
        for (Word word : selection.blankWords()) {
            String normalized = normalizeAnswer(word.getWord());
            if (!StringUtils.hasText(normalized) || !normalizedBlankWords.add(normalized)) {
                throw new BizException(ErrorCode.AI_CALL_FAILED, "AI 完形填空挖空词重复或为空");
            }
            List<WordOccurrence> matches = findWordOccurrences(passage, word);
            if (matches.isEmpty()) {
                throw new BizException(ErrorCode.AI_CALL_FAILED, "AI 完形填空文章未包含挖空词：" + word.getWord());
            }
            if (matches.size() > 1) {
                throw new BizException(ErrorCode.AI_CALL_FAILED, "AI 完形填空文章重复出现挖空词：" + word.getWord());
            }
            occurrences.add(matches.get(0));
        }

        occurrences.sort((left, right) -> Integer.compare(left.start(), right.start()));
        Map<String, String> explanationMap = buildExplanationMap(content);
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
            blank.setAnswerWord(word.getWord());
            blank.setHint(null);
            blank.setExplanation(explanationMap.getOrDefault(normalizeAnswer(word.getWord()), fallbackExplanation(word)));
            blank.setDeleted(0);
            blanks.add(blank);
            blankNo++;
        }
        maskedPassage.append(passage.substring(cursor));
        return new ProgrammaticClozeDraft(maskedPassage.toString(), blanks);
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

    private List<WordOccurrence> findWordOccurrences(String text, Word word) {
        if (!StringUtils.hasText(text) || word == null || !StringUtils.hasText(word.getWord())) {
            return List.of();
        }
        Matcher matcher = wordPattern(word.getWord()).matcher(text);
        List<WordOccurrence> occurrences = new ArrayList<>();
        while (matcher.find()) {
            occurrences.add(new WordOccurrence(word, matcher.start(), matcher.end()));
        }
        return occurrences;
    }

    private Pattern wordPattern(String word) {
        return Pattern.compile("(?i)(?<![A-Za-z])" + Pattern.quote(word.trim()) + "(?![A-Za-z])");
    }

    private boolean containsCjk(String text) {
        return StringUtils.hasText(text) && Pattern.compile("[\\p{IsHan}]").matcher(text).find();
    }

    private Map<String, String> buildExplanationMap(JsonNode content) {
        Map<String, String> explanations = new LinkedHashMap<>();
        JsonNode explanationsNode = content.path("explanations");
        if (!explanationsNode.isArray()) {
            return explanations;
        }
        for (JsonNode explanationNode : explanationsNode) {
            String word = explanationNode.path("word").asText("");
            String explanation = explanationNode.path("explanation").asText("");
            if (StringUtils.hasText(word) && StringUtils.hasText(explanation)) {
                explanations.put(normalizeAnswer(word), explanation);
            }
        }
        return explanations;
    }

    private List<String> normalizeCandidateWords(List<Word> targetWords) {
        LinkedHashSet<String> words = new LinkedHashSet<>();
        targetWords.stream().map(Word::getWord).filter(StringUtils::hasText).forEach(words::add);
        return words.stream().toList();
    }

    private JsonNode buildFallbackContent(ClozeWordSelection selection, BizException lastError) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("title", "LexiFlow 本组单词完形练习");
        root.put("passage", buildFallbackPassage(selection));
        root.put("explanation", "AI 返回内容暂未通过解析或文本检测，系统已根据本组单词生成可继续练习的兜底题。"
                + (lastError == null || !StringUtils.hasText(lastError.getCustomMessage()) ? "" : "最近一次原因：" + lastError.getCustomMessage()));

        ArrayNode explanations = root.putArray("explanations");
        for (Word word : selection.blankWords()) {
            ObjectNode explanation = explanations.addObject();
            explanation.put("word", word.getWord());
            explanation.put("explanation", fallbackExplanation(word));
        }
        return root;
    }

    private String buildFallbackPassage(ClozeWordSelection selection) {
        StringBuilder passage = new StringBuilder();
        passage.append("A student prepared for a busy week by making practical decisions. ");
        for (Word word : selection.blankWords()) {
            passage.append("The report used ")
                    .append(word.getWord())
                    .append(" to describe one important part of the situation")
                    .append(". ");
        }
        List<Word> backgroundWords = selection.backgroundWords();
        if (!backgroundWords.isEmpty()) {
            passage.append("The wider context also mentioned ");
            passage.append(backgroundWords.stream()
                    .map(Word::getWord)
                    .filter(StringUtils::hasText)
                    .limit(6)
                    .collect(Collectors.joining(", ")));
            passage.append(" during the discussion.");
        }
        return passage.toString();
    }

    private String fallbackExplanation(Word word) {
        String definition = safe(word.getPrimaryDefinition());
        if (!StringUtils.hasText(definition)) {
            return "该空对应本组目标词 " + word.getWord() + "。";
        }
        return "该空对应 " + word.getWord() + "，核心含义是：" + definition;
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

    private record WordOccurrence(Word word, int start, int end) {
    }

    private record ClozeWordSelection(List<Word> targetWords, List<Word> blankWords) {
        private List<Word> backgroundWords() {
            Set<Long> blankWordIds = blankWords.stream().map(Word::getId).collect(Collectors.toSet());
            return targetWords.stream().filter(word -> !blankWordIds.contains(word.getId())).toList();
        }
    }

}
