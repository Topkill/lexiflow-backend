package com.lexiflow.quiz.cloze.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexiflow.ai.content.domain.AiContentType;
import com.lexiflow.ai.core.dto.AiChatCompletionResult;
import com.lexiflow.ai.core.dto.AiPrompt;
import com.lexiflow.ai.core.service.AiGatewayService;
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
import com.lexiflow.study.progress.domain.StudyScene;
import com.lexiflow.study.progress.domain.WrongWord;
import com.lexiflow.study.progress.mapper.StudyEventMapper;
import com.lexiflow.study.progress.mapper.WrongWordMapper;
import com.lexiflow.study.task.domain.DailyTask;
import com.lexiflow.study.task.domain.DailyTaskItem;
import com.lexiflow.study.task.domain.DailyTaskItemType;
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
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ClozeQuizService {

    private static final String SYSTEM_PROMPT = "你是 LexiFlow 的 AI 英语测验出题助手。请只输出合法 JSON，不要输出 Markdown、解释性前后缀或代码块。题目面向备考大学生，短文自然连贯，所有空格答案必须来自候选词。";

    private final AsyncTaskService asyncTaskService;
    private final AiGatewayService aiGatewayService;
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
        String requestJson = toJson(Map.of(
                "dailyTaskId", String.valueOf(request.dailyTaskId()),
                "sourceType", request.safeSourceType().name(),
                "targetWordCount", request.targetWordCount()
        ));
        AsyncTask task = asyncTaskService.createTask(userId, AsyncTaskType.AI_CLOZE, requestJson);
        try {
            asyncTaskService.markRunning(task.getId(), "正在生成完形填空", 20);
            ClozeQuiz quiz = generateQuiz(userId, dailyTask, wordbookId, task.getId(), request.safeSourceType(), request.targetWordCount());
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
        List<Word> targetWords = selectTargetWords(userId, dailyTask, wordbookId, sourceType, targetWordCount);
        if (targetWords.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "今日任务暂无可用于生成完形填空的目标词");
        }
        AiPrompt prompt = buildPrompt(dailyTask, wordbookId, sourceType, targetWords);
        AiChatCompletionResult result = aiGatewayService.generateJson(userId, AiContentType.CLOZE, prompt);
        JsonNode content = parseJson(cleanJson(result.content()));
        return saveQuiz(userId, dailyTask, wordbookId, asyncTaskId, sourceType, targetWords, content);
    }

    private List<Word> selectTargetWords(Long userId, DailyTask dailyTask, Long wordbookId, ClozeSourceType sourceType, int targetWordCount) {
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
            return List.of();
        }
        Map<Long, Word> wordMap = wordMapper.selectBatchIds(limitedIds).stream()
                .collect(Collectors.toMap(Word::getId, Function.identity()));
        return limitedIds.stream()
                .map(wordMap::get)
                .filter(Objects::nonNull)
                .toList();
    }

    private ClozeQuiz saveQuiz(Long userId, DailyTask dailyTask, Long wordbookId, Long asyncTaskId, ClozeSourceType sourceType, List<Word> targetWords, JsonNode content) {
        JsonNode blanksNode = content.path("blanks");
        if (!blanksNode.isArray() || blanksNode.isEmpty()) {
            throw new BizException(ErrorCode.AI_CALL_FAILED, "AI 完形填空缺少空格数据");
        }
        Map<String, Word> wordMap = targetWords.stream()
                .collect(Collectors.toMap(word -> normalizeAnswer(word.getDisplayText()), Function.identity(), (left, right) -> left));
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
            blank.setAnswerWord(word.getDisplayText());
            blank.setHint(blankNode.path("hint").asText(word.getPrimaryDefinition()));
            blank.setExplanation(blankNode.path("explanation").asText(null));
            blank.setDeleted(0);
            blank.setVersion(0);
            blankDrafts.add(blank);
            blankNo++;
        }
        if (blankDrafts.isEmpty()) {
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
        quiz.setCandidateWords(toJson(normalizeCandidateWords(content.path("candidateWords"), targetWords)));
        quiz.setTargetWordIds(toJson(targetWords.stream().map(word -> String.valueOf(word.getId())).toList()));
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

    private AiPrompt buildPrompt(DailyTask dailyTask, Long wordbookId, ClozeSourceType sourceType, List<Word> targetWords) {
        List<Map<String, Object>> words = targetWords.stream()
                .map(word -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("wordId", String.valueOf(word.getId()));
                    item.put("word", word.getDisplayText());
                    item.put("pos", safe(word.getPrimaryPos()));
                    item.put("definition", safe(word.getPrimaryDefinition()));
                    item.put("example", safe(word.getExampleSentence()));
                    return item;
                })
                .toList();
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("dailyTaskId", String.valueOf(dailyTask.getId()));
        context.put("sourceType", sourceType.name());
        context.put("wordbookId", String.valueOf(wordbookId));
        context.put("targetWords", words);
        String sourceJson = toJson(context);
        String schema = "输出 JSON 对象：title 字符串；passage 字符串，使用 ___1___、___2___ 这样的占位符；candidateWords 字符串数组，包含所有目标词和少量干扰词；blanks 数组，每项包含 blankNo、answer、hint、explanation；explanation 字符串。";
        String userPrompt = schema + "\n请围绕同一校园或备考主题生成一段 80-140 词英文短文，空格数量与目标词数量尽量一致。\n" + sourceJson;
        return new AiPrompt(SYSTEM_PROMPT, userPrompt, sha256(sourceJson));
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
        targetWords.stream().map(Word::getDisplayText).filter(StringUtils::hasText).forEach(words::add);
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
        try {
            JsonNode node = objectMapper.readTree(json);
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
}