package com.lexiflow.study.task.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lexiflow.common.error.ErrorCode;
import com.lexiflow.common.exception.BizException;
import com.lexiflow.study.domain.StudyPlan;
import com.lexiflow.study.mapper.StudyPlanMapper;
import com.lexiflow.study.progress.domain.FavoriteWord;
import com.lexiflow.study.progress.domain.MasteryStatus;
import com.lexiflow.study.progress.domain.StudyEvent;
import com.lexiflow.study.progress.domain.StudyFeedback;
import com.lexiflow.study.progress.domain.StudyScene;
import com.lexiflow.study.progress.domain.UserWordState;
import com.lexiflow.study.progress.domain.WrongWord;
import com.lexiflow.study.progress.mapper.FavoriteWordMapper;
import com.lexiflow.study.progress.mapper.StudyEventMapper;
import com.lexiflow.study.progress.mapper.UserWordStateMapper;
import com.lexiflow.study.progress.mapper.WrongWordMapper;
import com.lexiflow.study.service.StudyPlanService;
import com.lexiflow.study.task.domain.DailyTask;
import com.lexiflow.study.task.domain.DailyTaskItem;
import com.lexiflow.study.task.domain.DailyTaskItemStatus;
import com.lexiflow.study.task.domain.DailyTaskItemType;
import com.lexiflow.study.task.domain.DailyTaskStatus;
import com.lexiflow.study.task.dto.DailyTaskItemResponse;
import com.lexiflow.study.task.dto.DailyTaskPlanResponse;
import com.lexiflow.study.task.dto.DailyTaskResponse;
import com.lexiflow.study.task.dto.SubmitFeedbackRequest;
import com.lexiflow.study.task.dto.SubmitFeedbackResponse;
import com.lexiflow.study.task.dto.TaskItemCardResponse;
import com.lexiflow.study.task.dto.TaskProgressResponse;
import com.lexiflow.study.task.mapper.DailyTaskItemMapper;
import com.lexiflow.study.task.mapper.DailyTaskMapper;
import com.lexiflow.wordbook.domain.Word;
import com.lexiflow.wordbook.domain.Wordbook;
import com.lexiflow.wordbook.dto.WordbookWordPickRow;
import com.lexiflow.wordbook.mapper.WordMapper;
import com.lexiflow.wordbook.mapper.WordbookWordMapper;
import com.lexiflow.wordbook.service.WordbookService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DailyTaskService {

    private final DailyTaskMapper dailyTaskMapper;
    private final DailyTaskItemMapper dailyTaskItemMapper;
    private final StudyPlanMapper studyPlanMapper;
    private final StudyPlanService studyPlanService;
    private final WordbookService wordbookService;
    private final WordbookWordMapper wordbookWordMapper;
    private final WordMapper wordMapper;
    private final UserWordStateMapper userWordStateMapper;
    private final StudyEventMapper studyEventMapper;
    private final WrongWordMapper wrongWordMapper;
    private final FavoriteWordMapper favoriteWordMapper;

    @Transactional
    public DailyTaskResponse getTodayTask(Long userId) {
        StudyPlan plan = studyPlanService.getPrimaryActivePlanEntity(userId);
        LocalDate today = LocalDate.now();
        DailyTask task = findTodayTask(userId, plan.getId(), today);
        if (task == null) {
            task = generateTodayTask(userId, plan, today);
        }
        return toResponse(task, plan);
    }

    public TaskItemCardResponse getCard(Long userId, Long itemId) {
        DailyTaskItem item = getOwnedTaskItem(userId, itemId);
        Word word = getWord(item.getWordId());
        UserWordState state = findUserWordState(userId, item.getWordbookId(), item.getWordId());
        boolean favorite = isFavorite(userId, item.getWordbookId(), item.getWordId());
        MasteryStatus masteryStatus = state == null ? MasteryStatus.NEW : state.getMasteryStatus();
        return TaskItemCardResponse.from(item, word, favorite, masteryStatus);
    }

    @Transactional
    public SubmitFeedbackResponse submitFeedback(Long userId, Long itemId, SubmitFeedbackRequest request) {
        DailyTaskItem item = getOwnedTaskItem(userId, itemId);
        if (item.getStatus() != DailyTaskItemStatus.PENDING) {
            throw new BizException(ErrorCode.TASK_ITEM_NOT_SUBMITTABLE);
        }

        StudyScene scene = toStudyScene(item.getItemType());
        Sm2Result sm2Result = updateWordState(userId, item, request.feedback(), scene);
        StudyEvent event = createStudyEvent(userId, item, request.feedback(), request.durationSeconds(), scene, sm2Result.qualityScore());
        if (request.feedback() == StudyFeedback.UNKNOWN) {
            upsertWrongWord(userId, item, event.getId(), scene);
        }
        updateStudyPlanProgress(item, scene, sm2Result);

        item.setStatus(DailyTaskItemStatus.DONE);
        item.setFeedback(request.feedback().name());
        item.setDoneAt(LocalDateTime.now());
        dailyTaskItemMapper.updateById(item);

        DailyTask task = updateDailyTaskProgress(item.getDailyTaskId());
        TaskProgressResponse progress = TaskProgressResponse.from(task.getDoneCount(), totalCount(task));
        return SubmitFeedbackResponse.from(item, request.feedback(), sm2Result.nextReviewDate(), task.getStatus() == DailyTaskStatus.DONE, progress);
    }

    private DailyTask findTodayTask(Long userId, Long planId, LocalDate today) {
        return dailyTaskMapper.selectOne(new LambdaQueryWrapper<DailyTask>()
                .eq(DailyTask::getUserId, userId)
                .eq(DailyTask::getPlanId, planId)
                .eq(DailyTask::getTaskDate, today)
                .last("LIMIT 1"));
    }

    private DailyTask generateTodayTask(Long userId, StudyPlan plan, LocalDate today) {
        List<WordbookWordPickRow> newWords = wordbookWordMapper.selectNewWordCandidates(
                plan.getWordbookId(),
                plan.getCurrentSequenceNo(),
                plan.getDailyNewWords()
        );

        DailyTask task = new DailyTask();
        task.setUserId(userId);
        task.setPlanId(plan.getId());
        task.setTaskDate(today);
        task.setStatus(DailyTaskStatus.PENDING);
        task.setNewCount(newWords.size());
        task.setReviewCount(0);
        task.setExtraCount(0);
        task.setDoneCount(0);
        task.setSkippedCount(0);
        task.setDeleted(0);
        task.setVersion(0);
        dailyTaskMapper.insert(task);

        for (WordbookWordPickRow row : newWords) {
            DailyTaskItem item = new DailyTaskItem();
            item.setDailyTaskId(task.getId());
            item.setUserId(userId);
            item.setPlanId(plan.getId());
            item.setWordbookId(plan.getWordbookId());
            item.setWordId(row.wordId());
            item.setItemType(DailyTaskItemType.NEW);
            item.setStatus(DailyTaskItemStatus.PENDING);
            item.setSequenceNo(row.sequenceNo());
            item.setDeleted(0);
            item.setVersion(0);
            dailyTaskItemMapper.insert(item);
        }

        if (!newWords.isEmpty()) {
            int maxSequenceNo = newWords.stream()
                    .map(WordbookWordPickRow::sequenceNo)
                    .max(Integer::compareTo)
                    .orElse(plan.getCurrentSequenceNo());
            plan.setCurrentSequenceNo(maxSequenceNo);
            plan.setLearnedCount(plan.getLearnedCount() + newWords.size());
            studyPlanMapper.updateById(plan);
        }
        return task;
    }

    private DailyTaskResponse toResponse(DailyTask task, StudyPlan plan) {
        Wordbook wordbook = wordbookService.getEnabledWordbook(plan.getWordbookId());
        List<DailyTaskItem> items = dailyTaskItemMapper.selectList(new LambdaQueryWrapper<DailyTaskItem>()
                .eq(DailyTaskItem::getDailyTaskId, task.getId())
                .orderByAsc(DailyTaskItem::getSequenceNo)
                .orderByAsc(DailyTaskItem::getId));
        List<DailyTaskItemResponse> itemResponses = buildItemResponses(items);
        DailyTaskPlanResponse planResponse = new DailyTaskPlanResponse(String.valueOf(plan.getId()), wordbook.getName());
        return DailyTaskResponse.from(task, planResponse, itemResponses);
    }

    private List<DailyTaskItemResponse> buildItemResponses(List<DailyTaskItem> items) {
        if (items.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> wordIds = items.stream().map(DailyTaskItem::getWordId).toList();
        Map<Long, Word> wordMap = wordMapper.selectBatchIds(wordIds).stream()
                .collect(Collectors.toMap(Word::getId, Function.identity()));
        return items.stream()
                .sorted(Comparator.comparing(DailyTaskItem::getSequenceNo).thenComparing(DailyTaskItem::getId))
                .map(item -> {
                    Word word = wordMap.get(item.getWordId());
                    if (word == null) {
                        throw new BizException(ErrorCode.TODAY_TASK_NOT_FOUND);
                    }
                    return DailyTaskItemResponse.from(item, word);
                })
                .toList();
    }

    private DailyTaskItem getOwnedTaskItem(Long userId, Long itemId) {
        DailyTaskItem item = dailyTaskItemMapper.selectOne(new LambdaQueryWrapper<DailyTaskItem>()
                .eq(DailyTaskItem::getId, itemId)
                .eq(DailyTaskItem::getUserId, userId)
                .last("LIMIT 1"));
        if (item == null) {
            throw new BizException(ErrorCode.TODAY_TASK_NOT_FOUND);
        }
        return item;
    }

    private Word getWord(Long wordId) {
        Word word = wordMapper.selectById(wordId);
        if (word == null) {
            throw new BizException(ErrorCode.WORD_NOT_FOUND);
        }
        return word;
    }

    private UserWordState findUserWordState(Long userId, Long wordbookId, Long wordId) {
        return userWordStateMapper.selectOne(new LambdaQueryWrapper<UserWordState>()
                .eq(UserWordState::getUserId, userId)
                .eq(UserWordState::getWordbookId, wordbookId)
                .eq(UserWordState::getWordId, wordId)
                .last("LIMIT 1"));
    }

    private boolean isFavorite(Long userId, Long wordbookId, Long wordId) {
        return favoriteWordMapper.selectCount(new LambdaQueryWrapper<FavoriteWord>()
                .eq(FavoriteWord::getUserId, userId)
                .eq(FavoriteWord::getWordbookId, wordbookId)
                .eq(FavoriteWord::getWordId, wordId)) > 0;
    }

    private Sm2Result updateWordState(Long userId, DailyTaskItem item, StudyFeedback feedback, StudyScene scene) {
        UserWordState state = findUserWordState(userId, item.getWordbookId(), item.getWordId());
        MasteryStatus oldMasteryStatus = state == null ? MasteryStatus.NEW : state.getMasteryStatus();
        if (state == null) {
            state = newDefaultWordState(userId, item);
            applyFeedback(state, feedback, scene);
            userWordStateMapper.insert(state);
        } else {
            state.setPlanId(item.getPlanId());
            applyFeedback(state, feedback, scene);
            userWordStateMapper.updateById(state);
        }
        return new Sm2Result(state.getNextReviewDate(), qualityScore(feedback), oldMasteryStatus, state.getMasteryStatus());
    }

    private UserWordState newDefaultWordState(Long userId, DailyTaskItem item) {
        UserWordState state = new UserWordState();
        state.setUserId(userId);
        state.setWordbookId(item.getWordbookId());
        state.setWordId(item.getWordId());
        state.setPlanId(item.getPlanId());
        state.setMasteryStatus(MasteryStatus.NEW);
        state.setLearned(false);
        state.setRepetition(0);
        state.setIntervalDays(0);
        state.setEasinessFactor(new BigDecimal("2.50"));
        state.setWrongCount(0);
        state.setCorrectCount(0);
        state.setDeleted(0);
        state.setVersion(0);
        return state;
    }

    private void applyFeedback(UserWordState state, StudyFeedback feedback, StudyScene scene) {
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        int quality = qualityScore(feedback);
        int nextRepetition;
        int nextInterval;
        BigDecimal nextEf = nextEasinessFactor(state.getEasinessFactor(), quality);

        if (feedback == StudyFeedback.UNKNOWN) {
            nextRepetition = 0;
            nextInterval = 1;
            int nextWrongCount = state.getWrongCount() + 1;
            state.setWrongCount(nextWrongCount);
            state.setMasteryStatus(nextWrongCount >= 3 ? MasteryStatus.DIFFICULT : MasteryStatus.LEARNING);
        } else {
            nextRepetition = state.getRepetition() + 1;
            nextInterval = nextIntervalDays(nextRepetition, state.getIntervalDays(), nextEf, feedback);
            state.setCorrectCount(state.getCorrectCount() + 1);
            state.setMasteryStatus(nextRepetition >= 3 && feedback == StudyFeedback.KNOWN ? MasteryStatus.MASTERED : MasteryStatus.REVIEWING);
        }

        state.setLearned(true);
        state.setRepetition(nextRepetition);
        state.setIntervalDays(nextInterval);
        state.setEasinessFactor(nextEf);
        state.setNextReviewDate(today.plusDays(nextInterval));
        state.setLastFeedback(feedback);
        state.setLastStudiedAt(now);
        if (scene == StudyScene.REVIEW) {
            state.setLastReviewedAt(now);
        }
    }

    private int nextIntervalDays(int repetition, int previousInterval, BigDecimal ef, StudyFeedback feedback) {
        if (feedback == StudyFeedback.VAGUE) {
            return Math.max(1, previousInterval + 1);
        }
        if (repetition <= 1) {
            return 1;
        }
        if (repetition == 2) {
            return 3;
        }
        return Math.max(1, BigDecimal.valueOf(Math.max(previousInterval, 3))
                .multiply(ef)
                .setScale(0, RoundingMode.HALF_UP)
                .intValue());
    }

    private BigDecimal nextEasinessFactor(BigDecimal currentEf, int quality) {
        BigDecimal ef = currentEf == null ? new BigDecimal("2.50") : currentEf;
        BigDecimal q = BigDecimal.valueOf(quality);
        BigDecimal delta = new BigDecimal("0.10")
                .subtract(new BigDecimal("5").subtract(q).multiply(new BigDecimal("0.08")))
                .subtract(new BigDecimal("5").subtract(q).multiply(new BigDecimal("5").subtract(q)).multiply(new BigDecimal("0.02")));
        BigDecimal next = ef.add(delta).setScale(2, RoundingMode.HALF_UP);
        return next.max(new BigDecimal("1.30"));
    }

    private int qualityScore(StudyFeedback feedback) {
        return switch (feedback) {
            case UNKNOWN -> 2;
            case VAGUE -> 4;
            case KNOWN -> 5;
        };
    }

    private StudyEvent createStudyEvent(Long userId, DailyTaskItem item, StudyFeedback feedback, Integer durationSeconds, StudyScene scene, int qualityScore) {
        StudyEvent event = new StudyEvent();
        event.setUserId(userId);
        event.setPlanId(item.getPlanId());
        event.setWordbookId(item.getWordbookId());
        event.setWordId(item.getWordId());
        event.setDailyTaskId(item.getDailyTaskId());
        event.setDailyTaskItemId(item.getId());
        event.setScene(scene);
        event.setFeedback(feedback);
        event.setQualityScore(qualityScore);
        event.setIsCorrect(feedback != StudyFeedback.UNKNOWN);
        event.setDurationSeconds(durationSeconds);
        studyEventMapper.insert(event);
        return event;
    }

    private void upsertWrongWord(Long userId, DailyTaskItem item, Long eventId, StudyScene scene) {
        WrongWord wrongWord = wrongWordMapper.selectOne(new LambdaQueryWrapper<WrongWord>()
                .eq(WrongWord::getUserId, userId)
                .eq(WrongWord::getWordbookId, item.getWordbookId())
                .eq(WrongWord::getWordId, item.getWordId())
                .last("LIMIT 1"));
        if (wrongWord == null) {
            wrongWord = new WrongWord();
            wrongWord.setUserId(userId);
            wrongWord.setWordbookId(item.getWordbookId());
            wrongWord.setWordId(item.getWordId());
            wrongWord.setWrongCount(1);
            wrongWord.setLastSource(scene);
            wrongWord.setLastEventId(eventId);
            wrongWord.setLastWrongAt(LocalDateTime.now());
            wrongWord.setResolved(false);
            wrongWord.setDeleted(0);
            wrongWord.setVersion(0);
            wrongWordMapper.insert(wrongWord);
            return;
        }
        wrongWord.setWrongCount(wrongWord.getWrongCount() + 1);
        wrongWord.setLastSource(scene);
        wrongWord.setLastEventId(eventId);
        wrongWord.setLastWrongAt(LocalDateTime.now());
        wrongWord.setResolved(false);
        wrongWord.setResolvedAt(null);
        wrongWordMapper.updateById(wrongWord);
    }

    private DailyTask updateDailyTaskProgress(Long dailyTaskId) {
        DailyTask task = dailyTaskMapper.selectById(dailyTaskId);
        if (task == null) {
            throw new BizException(ErrorCode.TODAY_TASK_NOT_FOUND);
        }
        Long doneCount = dailyTaskItemMapper.selectCount(new LambdaQueryWrapper<DailyTaskItem>()
                .eq(DailyTaskItem::getDailyTaskId, dailyTaskId)
                .eq(DailyTaskItem::getStatus, DailyTaskItemStatus.DONE));
        task.setDoneCount(doneCount.intValue());
        if (task.getDoneCount() >= totalCount(task) && totalCount(task) > 0) {
            task.setStatus(DailyTaskStatus.DONE);
            task.setCompletedAt(LocalDateTime.now());
        }
        dailyTaskMapper.updateById(task);
        return task;
    }

    private void updateStudyPlanProgress(DailyTaskItem item, StudyScene scene, Sm2Result sm2Result) {
        StudyPlan plan = studyPlanMapper.selectById(item.getPlanId());
        if (plan == null) {
            throw new BizException(ErrorCode.STUDY_PLAN_NOT_FOUND);
        }
        if (scene == StudyScene.REVIEW) {
            plan.setReviewedCount(plan.getReviewedCount() + 1);
        }
        if (sm2Result.oldMasteryStatus() != MasteryStatus.MASTERED && sm2Result.newMasteryStatus() == MasteryStatus.MASTERED) {
            plan.setMasteredCount(plan.getMasteredCount() + 1);
        } else if (sm2Result.oldMasteryStatus() == MasteryStatus.MASTERED && sm2Result.newMasteryStatus() != MasteryStatus.MASTERED) {
            plan.setMasteredCount(Math.max(0, plan.getMasteredCount() - 1));
        }
        studyPlanMapper.updateById(plan);
    }

    private int totalCount(DailyTask task) {
        return task.getNewCount() + task.getReviewCount() + task.getExtraCount();
    }

    private StudyScene toStudyScene(DailyTaskItemType itemType) {
        return switch (itemType) {
            case NEW -> StudyScene.NEW;
            case REVIEW -> StudyScene.REVIEW;
            case EXTRA -> StudyScene.EXTRA;
        };
    }

    private record Sm2Result(LocalDate nextReviewDate, int qualityScore, MasteryStatus oldMasteryStatus, MasteryStatus newMasteryStatus) {
    }
}
