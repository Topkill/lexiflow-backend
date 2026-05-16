package com.lexiflow.quiz.cloze.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.lexiflow.study.task.domain.DailyTask;
import com.lexiflow.study.task.domain.DailyTaskItem;
import com.lexiflow.study.task.domain.DailyTaskItemStatus;
import com.lexiflow.study.task.domain.DailyTaskItemType;
import com.lexiflow.study.task.domain.DailyTaskStatus;
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
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ClozeQuizService {

    private static final String SYSTEM_PROMPT = "你是 LexiFlow 的 AI 英语测验出题助手。请只输出合法 JSON，不要输出 Markdown、解释性前后缀或代码块。题目面向备考大学生，短文自然连贯，所有空格答案必须来自候选词。";
    private static final int COMPLETED_GROUP_BLANK_COUNT = 10;
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
            answer.setVersion(0);
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
        attempt.setVersion(0);
        clozeAttemptMapper.insert(attempt);

        for (ClozeAttemptAnswer answer : answerEntities) {
            answer.setAttemptId(attempt.getId());
            clozeAttemptAnswerMapper.insert(answer);
            StudyEvent event = createStudyEvent(userId, quiz, answer, request.durationSeconds(), attempt.getId());
            if (!answer.getCorrect()) {
                upsertWrongWord(userId, quiz.getWordbookId(), answer.getWordId(), event.getId());
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
            AiChatCompletionResult result = aiGatewayService.generateJson(userId, AiContentType.CLOZE, prompt);
            JsonNode content = parseJson(result.content());
            try {
                validateGeneratedContent(content, selection);
                upsertClozeCache(userId, wordbookId, sourceHash, content);
                return saveQuiz(userId, dailyTask, wordbookId, asyncTaskId, sourceType, selection, content);
            } catch (BizException ex) {
                lastValidationError = ex;
            }
        }
        throw lastValidationError == null ? new BizException(ErrorCode.AI_CALL_FAILED, "AI 完形填空生成结果未通过文本检测") : lastValidationError;
    }

    private ClozeWordSelection selectClozeWords(Long userId, DailyTask dailyTask, Long wordbookId, ClozeSourceType sourceType, int targetWordCount) {
        if (sourceType == ClozeSourceType.COMPLETED_GROUP) {
            return selectCompletedGroupWords(userId, dailyTask);
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

    private ClozeWordSelection selectCompletedGroupWords(Long userId, DailyTask dailyTask) {
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
        if (targetWords.size() < COMPLETED_GROUP_BLANK_COUNT) {
            throw new BizException(ErrorCode.BAD_REQUEST, "本组已完成单词不足 10 个，暂不能生成 10 空完形填空");
        }
        Map<Long, StudyFeedback> feedbackMap = selectFeedbackMap(userId, dailyTask.getId(), itemMap);
        List<Word> blankWords = clozeBlankWordSelector.selectBlankWords(targetWords, feedbackMap, COMPLETED_GROUP_BLANK_COUNT, dailyTask.getId());
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

    private String buildClozeSourceHash(Long userId, DailyTask dailyTask, Long wordbookId, ClozeSourceType sourceType, ClozeWordSelection selection) {
        Map<String, Object> source = new LinkedHashMap<>();
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
        JsonNode blanksNode = content.path("blanks");
        if (!blanksNode.isArray() || blanksNode.isEmpty()) {
            throw new BizException(ErrorCode.AI_CALL_FAILED, "AI 完形填空缺少空格数据");
        }
        Map<String, Word> wordMap = selection.blankWords().stream()
                .collect(Collectors.toMap(word -> normalizeAnswer(word.getWord()), Function.identity(), (left, right) -> left));
        List<ClozeQuizBlank> blankDrafts = new ArrayList<>();
        int blankNo = 1;
        for (JsonNode blankNode : blanksNode) {
            String answerWord = blankNode.path("answer").asText();
            Word word = wordMap.get(normalizeAnswer(answerWord));
            if (word == null) {
                continue;
            }
            ClozeQuizBlank blank = new ClozeQuizBlank();
            blank.setBlankNo(blankNode.path("blankNo").asInt(blankNo));
            blank.setWordId(word.getId());
            blank.setAnswerWord(word.getWord());
            blank.setHint(blankNode.path("hint").asText(word.getPrimaryDefinition()));
            blank.setExplanation(blankNode.path("explanation").asText(null));
            blank.setDeleted(0);
            blank.setVersion(0);
            blankDrafts.add(blank);
            blankNo++;
        }
        if (blankDrafts.size() != selection.blankWords().size()) {
            throw new BizException(ErrorCode.AI_CALL_FAILED, "AI 完形填空答案未匹配目标词");
        }

        ClozeQuiz quiz = new ClozeQuiz();
        quiz.setUserId(userId);
        quiz.setWordbookId(wordbookId);
        quiz.setDailyTaskId(dailyTask.getId());
        quiz.setAsyncTaskId(asyncTaskId);
        quiz.setSourceType(sourceType);
        quiz.setTitle(content.path("title").asText("LexiFlow Cloze Practice"));
        quiz.setPassage(content.path("passage").asText());
        quiz.setCandidateWords(toJson(normalizeCandidateWords(content.path("candidateWords"), selection.blankWords())));
        quiz.setTargetWordIds(toJson(selection.targetWords().stream().map(word -> String.valueOf(word.getId())).toList()));
        quiz.setExplanation(content.path("explanation").asText(null));
        quiz.setDeleted(0);
        quiz.setVersion(0);
        clozeQuizMapper.insert(quiz);
        for (ClozeQuizBlank blank : blankDrafts) {
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
        String schema = "输出 JSON 对象：title 字符串；passage 字符串，必须使用 ___1___、___2___ 这样的占位符；candidateWords 字符串数组；blanks 数组，每项包含 blankNo、answer、hint、explanation；explanation 字符串。";
        String rules = "严格规则：1. blanks 数量必须等于 blankCount；2. blankWords 中每个单词必须且只能作为一个空格答案出现；3. backgroundWords 中每个单词必须完整出现在 passage 文本中，不能被挖空；4. candidateWords 必须包含所有 blankWords，可加入少量干扰词；5. passage 要是一篇自然连贯的 100-180 词英文短文。";
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
                    item.put("definition", safe(word.getPrimaryDefinition()));
                    item.put("sentences", safe(word.getSentences()));
                    return item;
                })
                .toList();
    }

    private void validateGeneratedContent(JsonNode content, ClozeWordSelection selection) {
        JsonNode blanksNode = content.path("blanks");
        if (!blanksNode.isArray() || blanksNode.size() != selection.blankWords().size()) {
            throw new BizException(ErrorCode.AI_CALL_FAILED, "AI 完形填空空格数量不符合要求");
        }
        Set<String> expectedBlankWords = selection.blankWords().stream()
                .map(word -> normalizeAnswer(word.getWord()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> actualBlankWords = new LinkedHashSet<>();
        for (JsonNode blankNode : blanksNode) {
            actualBlankWords.add(normalizeAnswer(blankNode.path("answer").asText()));
        }
        if (!actualBlankWords.equals(expectedBlankWords)) {
            throw new BizException(ErrorCode.AI_CALL_FAILED, "AI 完形填空挖空词未按学习反馈规则命中");
        }
        String passage = content.path("passage").asText("");
        if (!StringUtils.hasText(passage)) {
            throw new BizException(ErrorCode.AI_CALL_FAILED, "AI 完形填空缺少文章内容");
        }
        List<String> missingWords = selection.backgroundWords().stream()
                .map(Word::getWord)
                .filter(word -> !containsWord(passage, word))
                .toList();
        if (!missingWords.isEmpty()) {
            throw new BizException(ErrorCode.AI_CALL_FAILED, "AI 完形填空文章未覆盖全部背景词：" + String.join(", ", missingWords));
        }
    }

    private boolean containsWord(String text, String word) {
        if (!StringUtils.hasText(text) || !StringUtils.hasText(word)) {
            return false;
        }
        return Pattern.compile("(?i)(?<![A-Za-z])" + Pattern.quote(word.trim()) + "(?![A-Za-z])").matcher(text).find();
    }

    private List<String> normalizeCandidateWords(JsonNode candidateWords, List<Word> targetWords) {
        LinkedHashSet<String> words = new LinkedHashSet<>();
        if (candidateWords.isArray()) {
            candidateWords.forEach(node -> {
                if (StringUtils.hasText(node.asText())) {
                    words.add(node.asText().trim());
                }
            });
        }
        targetWords.stream().map(Word::getWord).filter(StringUtils::hasText).forEach(words::add);
        return words.stream().toList();
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
        event.setQualityScore(answer.getCorrect() ? 5 : 2);
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
            wrongWord.setVersion(0);
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

    private record ClozeWordSelection(List<Word> targetWords, List<Word> blankWords) {
        private List<Word> backgroundWords() {
            Set<Long> blankWordIds = blankWords.stream().map(Word::getId).collect(Collectors.toSet());
            return targetWords.stream().filter(word -> !blankWordIds.contains(word.getId())).toList();
        }
    }

}
