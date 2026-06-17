package com.lexiflow.study.task.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.lexiflow.common.error.ErrorCode;
import com.lexiflow.common.exception.BizException;
import com.lexiflow.quiz.cloze.domain.ClozeAttempt;
import com.lexiflow.quiz.cloze.domain.ClozeQuiz;
import com.lexiflow.quiz.cloze.mapper.ClozeAttemptMapper;
import com.lexiflow.quiz.cloze.mapper.ClozeQuizMapper;
import com.lexiflow.quiz.cloze.service.ClozeQuizService;
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
import com.lexiflow.study.progress.service.SpacedRepetitionService;
import com.lexiflow.study.progress.service.SpacedRepetitionService.SpacedRepetitionResult;
import com.lexiflow.study.service.StudyPlanService;
import com.lexiflow.study.task.domain.DailyTask;
import com.lexiflow.study.task.domain.DailyTaskItem;
import com.lexiflow.study.task.domain.DailyTaskItemStatus;
import com.lexiflow.study.task.domain.DailyTaskItemType;
import com.lexiflow.study.task.domain.DailyTaskStatus;
import com.lexiflow.study.task.domain.DailyTaskType;
import com.lexiflow.study.task.dto.CreateWrongWordPracticeRequest;
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
import com.lexiflow.wordbook.dto.WordPickRow;
import com.lexiflow.wordbook.mapper.WordMapper;
import com.lexiflow.wordbook.service.WordbookService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DailyTaskService {

    private static final int REVIEW_SEQUENCE_BASE = -100_000;
    private static final int EXTRA_SEQUENCE_BASE = 1_000_000;

    private final DailyTaskMapper dailyTaskMapper;
    private final DailyTaskItemMapper dailyTaskItemMapper;
    private final StudyPlanMapper studyPlanMapper;
    private final StudyPlanService studyPlanService;
    private final WordbookService wordbookService;
    private final WordMapper wordMapper;
    private final UserWordStateMapper userWordStateMapper;
    private final StudyEventMapper studyEventMapper;
    private final WrongWordMapper wrongWordMapper;
    private final FavoriteWordMapper favoriteWordMapper;
    private final ClozeQuizMapper clozeQuizMapper;
    private final ClozeAttemptMapper clozeAttemptMapper;
    private final ClozeQuizService clozeQuizService;
    private final SpacedRepetitionService spacedRepetitionService;
    private final WordChoiceQuestionService wordChoiceQuestionService;

    @Transactional
    public DailyTaskResponse getTodayTask(Long userId) {
        StudyPlan plan = studyPlanService.getPrimaryActivePlanEntity(userId);
        LocalDate today = LocalDate.now();
        DailyTask task = findLatestDoneTaskAwaitingClozeAttempt(userId, plan.getId(), today);
        if (task != null) {
            return toResponse(task, plan);
        }

        task = findLatestPendingTask(userId, plan.getId());
        if (task == null) {
            task = generateTodayTask(userId, plan, today);
        } else if (shouldSyncDueReviewItems(task, today)) {
            task = rollUntouchedPendingTaskToToday(task, today);
            task = syncDueReviewItems(userId, plan, task, today);
        }
        return toResponse(task, plan);
    }

    public DailyTaskResponse getTask(Long userId, Long taskId) {
        DailyTask task = getOwnedTask(userId, taskId);
        StudyPlan plan = studyPlanMapper.selectById(task.getPlanId());
        if (plan == null || !plan.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.STUDY_PLAN_NOT_FOUND);
        }
        return toResponse(task, plan);
    }

    public TaskItemCardResponse getCard(Long userId, Long itemId) {
        DailyTaskItem item = getOwnedTaskItem(userId, itemId);
        Word word = getWord(item.getWordId());
        List<Word> dailyTaskWords = selectDailyTaskWords(item.getDailyTaskId());
        UserWordState state = findUserWordState(userId, item.getWordbookId(), item.getWordId());
        FavoriteWord favorite = findFavoriteWord(userId, item.getWordbookId(), item.getWordId());
        MasteryStatus masteryStatus = state == null ? MasteryStatus.NEW : state.getMasteryStatus();
        return TaskItemCardResponse.from(
                item,
                word,
                favorite == null ? null : favorite.getId(),
                masteryStatus,
                wordChoiceQuestionService.buildQuestion(item.getId(), item.getWordbookId(), word, dailyTaskWords)
        );
    }

    @Transactional
    public DailyTaskResponse createWrongWordPractice(Long userId, CreateWrongWordPracticeRequest request) {
        StudyPlan plan = studyPlanService.getPrimaryActivePlanEntity(userId);
        Long targetWordbookId = request.wordbookId() == null ? plan.getWordbookId() : request.wordbookId();
        if (!plan.getWordbookId().equals(targetWordbookId)) {
            throw new BizException(ErrorCode.WORDBOOK_NOT_FOUND);
        }
        LocalDate today = LocalDate.now();
        List<WrongWord> wrongWords = wrongWordMapper.selectList(new LambdaQueryWrapper<WrongWord>()
                .eq(WrongWord::getUserId, userId)
                .eq(WrongWord::getWordbookId, targetWordbookId)
                .eq(WrongWord::getResolved, false)
                .orderByDesc(WrongWord::getWrongCount)
                .orderByDesc(WrongWord::getLastWrongAt)
                .orderByAsc(WrongWord::getId)
                .last("LIMIT " + request.limit()));
        if (wrongWords.isEmpty()) {
            throw new BizException(ErrorCode.TODAY_TASK_NOT_FOUND);
        }

        DailyTask task = new DailyTask();
        task.setUserId(userId);
        task.setPlanId(plan.getId());
        task.setTaskDate(today);
        task.setGroupNo(nextGroupNo(userId, plan.getId(), today, DailyTaskType.WRONG_WORD_PRACTICE));
        task.setTaskType(DailyTaskType.WRONG_WORD_PRACTICE);
        task.setStatus(DailyTaskStatus.PENDING);
        task.setNewCount(0);
        task.setReviewCount(0);
        task.setExtraCount(0);
        task.setDoneCount(0);
        task.setSkippedCount(0);
        task.setDeleted(0);
        dailyTaskMapper.insert(task);

        insertExtraItems(task, wrongWords, countTaskItems(task.getId(), DailyTaskItemType.EXTRA));
        task = updateDailyTaskProgress(task.getId());
        return toResponse(task, plan);
    }

    @Transactional
    public SubmitFeedbackResponse submitFeedback(Long userId, Long itemId, SubmitFeedbackRequest request) {
        DailyTaskItem item = getOwnedTaskItem(userId, itemId);
        DailyTask sourceTask = getOwnedTask(userId, item.getDailyTaskId());
        if (!claimFeedbackSubmission(userId, item, request.feedback())) {
            return buildAlreadyAppliedFeedbackResponse(userId, item, request.feedback());
        }

        if (sourceTask.getTaskType() == DailyTaskType.WRONG_WORD_PRACTICE) {
            return submitWrongWordPracticeFeedback(userId, item, request);
        }

        StudyScene scene = toStudyScene(item.getItemType());
        SpacedRepetitionResult repetitionResult = spacedRepetitionService.applyFeedback(
                userId,
                item.getWordbookId(),
                item.getWordId(),
                item.getPlanId(),
                request.feedback(),
                scene
        );
        StudyEvent event = createStudyEvent(userId, item, request.feedback(), request.durationSeconds(), scene, repetitionResult.qualityScore());
        if (request.feedback() == StudyFeedback.UNKNOWN) {
            upsertWrongWord(userId, item, event.getId(), scene);
        } else if (item.getItemType() == DailyTaskItemType.EXTRA) {
            resolveWrongWord(userId, item);
        }
        boolean completed = request.feedback() == StudyFeedback.KNOWN;
        updateStudyPlanProgress(item, scene, repetitionResult, completed);

        DailyTask task = completed ? incrementDoneCountAndRefreshTask(item.getDailyTaskId()) : getDailyTaskForProgress(item.getDailyTaskId());
        TaskProgressResponse progress = TaskProgressResponse.from(task.getDoneCount(), totalCount(task));
        return SubmitFeedbackResponse.from(item, request.feedback(), repetitionResult.nextReviewDate(), task.getStatus() == DailyTaskStatus.DONE, progress);
    }

    private SubmitFeedbackResponse submitWrongWordPracticeFeedback(Long userId, DailyTaskItem item, SubmitFeedbackRequest request) {
        StudyScene scene = StudyScene.EXTRA;
        StudyEvent event = createStudyEvent(userId, item, request.feedback(), request.durationSeconds(), scene, qualityScore(request.feedback()));
        boolean completed = request.feedback() == StudyFeedback.KNOWN;
        if (completed) {
            resolveWrongWord(userId, item);
        } else {
            upsertWrongWord(userId, item, event.getId(), scene);
        }

        DailyTask task = completed ? incrementDoneCountAndRefreshTask(item.getDailyTaskId()) : getDailyTaskForProgress(item.getDailyTaskId());
        TaskProgressResponse progress = TaskProgressResponse.from(task.getDoneCount(), totalCount(task));
        return SubmitFeedbackResponse.from(item, request.feedback(), null, task.getStatus() == DailyTaskStatus.DONE, progress);
    }

    private boolean claimFeedbackSubmission(Long userId, DailyTaskItem item, StudyFeedback feedback) {
        DailyTaskItem update = new DailyTaskItem();
        update.setFeedback(feedback.name());
        if (feedback == StudyFeedback.KNOWN) {
            update.setStatus(DailyTaskItemStatus.DONE);
            update.setDoneAt(LocalDateTime.now());
        } else {
            update.setStatus(DailyTaskItemStatus.PENDING);
        }

        LambdaUpdateWrapper<DailyTaskItem> wrapper = new LambdaUpdateWrapper<DailyTaskItem>()
                .eq(DailyTaskItem::getId, item.getId())
                .eq(DailyTaskItem::getUserId, userId)
                .eq(DailyTaskItem::getStatus, DailyTaskItemStatus.PENDING);
        if (feedback == StudyFeedback.UNKNOWN) {
            wrapper.and(nested -> nested
                    .isNull(DailyTaskItem::getFeedback)
                    .or()
                    .ne(DailyTaskItem::getFeedback, StudyFeedback.UNKNOWN.name()));
        }

        boolean updated = dailyTaskItemMapper.update(update, wrapper) > 0;
        if (updated) {
            item.setFeedback(feedback.name());
            item.setStatus(feedback == StudyFeedback.KNOWN ? DailyTaskItemStatus.DONE : DailyTaskItemStatus.PENDING);
            item.setDoneAt(feedback == StudyFeedback.KNOWN ? update.getDoneAt() : null);
        }
        return updated;
    }

    private SubmitFeedbackResponse buildAlreadyAppliedFeedbackResponse(Long userId, DailyTaskItem item, StudyFeedback feedback) {
        DailyTaskItem current = getOwnedTaskItem(userId, item.getId());
        if (!isAlreadyAppliedFeedback(current, feedback)) {
            throw new BizException(ErrorCode.TASK_ITEM_NOT_SUBMITTABLE);
        }
        UserWordState state = findUserWordState(userId, current.getWordbookId(), current.getWordId());
        DailyTask task = getDailyTaskForProgress(current.getDailyTaskId());
        TaskProgressResponse progress = TaskProgressResponse.from(task.getDoneCount(), totalCount(task));
        LocalDate nextReviewDate = task.getTaskType() == DailyTaskType.WRONG_WORD_PRACTICE
                ? null
                : state == null ? null : state.getNextReviewDate();
        return SubmitFeedbackResponse.from(
                current,
                feedback,
                nextReviewDate,
                task.getStatus() == DailyTaskStatus.DONE,
                progress
        );
    }

    private boolean isAlreadyAppliedFeedback(DailyTaskItem item, StudyFeedback feedback) {
        if (!feedback.name().equals(item.getFeedback())) {
            return false;
        }
        if (feedback == StudyFeedback.KNOWN) {
            return item.getStatus() == DailyTaskItemStatus.DONE;
        }
        return item.getStatus() == DailyTaskItemStatus.PENDING;
    }

    private DailyTask incrementDoneCountAndRefreshTask(Long dailyTaskId) {
        dailyTaskMapper.update(new DailyTask(), new LambdaUpdateWrapper<DailyTask>()
                .eq(DailyTask::getId, dailyTaskId)
                .setSql("done_count = COALESCE(done_count, 0) + 1"));
        boolean completedNow = markTaskDoneIfComplete(dailyTaskId);
        DailyTask task = getDailyTaskForProgress(dailyTaskId);
        if (completedNow && task.getTaskType() == DailyTaskType.DAILY) {
            clozeQuizService.prefetchCompletedGroupCloze(task.getUserId(), task.getId());
        }
        return task;
    }

    private boolean markTaskDoneIfComplete(Long dailyTaskId) {
        DailyTask update = new DailyTask();
        update.setStatus(DailyTaskStatus.DONE);
        update.setCompletedAt(LocalDateTime.now());
        return dailyTaskMapper.update(update, new LambdaUpdateWrapper<DailyTask>()
                .eq(DailyTask::getId, dailyTaskId)
                .ne(DailyTask::getStatus, DailyTaskStatus.DONE)
                .apply("COALESCE(done_count, 0) >= COALESCE(new_count, 0) + COALESCE(review_count, 0) + COALESCE(extra_count, 0)")
                .apply("COALESCE(new_count, 0) + COALESCE(review_count, 0) + COALESCE(extra_count, 0) > 0")) > 0;
    }

    private DailyTask getDailyTaskForProgress(Long dailyTaskId) {
        DailyTask task = dailyTaskMapper.selectById(dailyTaskId);
        if (task == null) {
            throw new BizException(ErrorCode.TODAY_TASK_NOT_FOUND);
        }
        return task;
    }

    private int qualityScore(StudyFeedback feedback) {
        return switch (feedback) {
            case UNKNOWN -> 2;
            case KNOWN -> 4;
        };
    }

    private DailyTask findLatestPendingTask(Long userId, Long planId) {
        return dailyTaskMapper.selectOne(new LambdaQueryWrapper<DailyTask>()
                .eq(DailyTask::getUserId, userId)
                .eq(DailyTask::getPlanId, planId)
                .eq(DailyTask::getTaskType, DailyTaskType.DAILY)
                .eq(DailyTask::getStatus, DailyTaskStatus.PENDING)
                .orderByDesc(DailyTask::getTaskDate)
                .orderByDesc(DailyTask::getGroupNo)
                .orderByDesc(DailyTask::getId)
                .last("LIMIT 1"));
    }

    private DailyTask rollUntouchedPendingTaskToToday(DailyTask task, LocalDate today) {
        if (task.getTaskDate() == null || !task.getTaskDate().isBefore(today)) {
            return task;
        }
        if (hasTouchedTaskItems(task.getId())) {
            return task;
        }
        task.setTaskDate(today);
        dailyTaskMapper.updateById(task);
        return task;
    }

    private boolean shouldSyncDueReviewItems(DailyTask task, LocalDate today) {
        if (task.getTaskDate() == null || !task.getTaskDate().isBefore(today)) {
            return true;
        }
        return !hasTouchedTaskItems(task.getId());
    }

    private boolean hasTouchedTaskItems(Long dailyTaskId) {
        return dailyTaskItemMapper.selectCount(new LambdaQueryWrapper<DailyTaskItem>()
                .eq(DailyTaskItem::getDailyTaskId, dailyTaskId)
                .and(wrapper -> wrapper
                        .eq(DailyTaskItem::getStatus, DailyTaskItemStatus.DONE)
                        .or()
                        .isNotNull(DailyTaskItem::getFeedback)
                        .or()
                        .isNotNull(DailyTaskItem::getDoneAt))) > 0;
    }

    private DailyTask findLatestDoneTaskAwaitingClozeAttempt(Long userId, Long planId, LocalDate today) {
        List<DailyTask> doneTasks = dailyTaskMapper.selectList(new LambdaQueryWrapper<DailyTask>()
                .eq(DailyTask::getUserId, userId)
                .eq(DailyTask::getPlanId, planId)
                .eq(DailyTask::getTaskDate, today)
                .eq(DailyTask::getTaskType, DailyTaskType.DAILY)
                .eq(DailyTask::getStatus, DailyTaskStatus.DONE)
                .orderByDesc(DailyTask::getTaskDate)
                .orderByDesc(DailyTask::getGroupNo)
                .orderByDesc(DailyTask::getId));
        return doneTasks.stream()
                .filter(task -> !hasCompletedGroupClozeAttempt(task))
                .findFirst()
                .orElse(null);
    }

    private boolean hasCompletedGroupClozeAttempt(DailyTask task) {
        List<ClozeQuiz> quizzes = clozeQuizMapper.selectList(new LambdaQueryWrapper<ClozeQuiz>()
                .eq(ClozeQuiz::getDailyTaskId, task.getId())
                .orderByDesc(ClozeQuiz::getId));
        if (quizzes.isEmpty()) {
            return false;
        }
        return quizzes.stream().anyMatch(quiz -> hasClozeAttempt(task.getUserId(), quiz.getId()));
    }

    private DailyTask generateTodayTask(Long userId, StudyPlan plan, LocalDate today) {
        List<UserWordState> dueReviewStates = selectDueReviewStates(userId, plan, today, Set.of(), reviewWordsPerGroup(plan));
        List<WordPickRow> newWords = wordMapper.selectNewWordCandidates(
                plan.getWordbookId(),
                plan.getCurrentSequenceNo(),
                newWordsPerGroup(plan)
        );
        if (dueReviewStates.isEmpty() && newWords.isEmpty()) {
            throw new BizException(ErrorCode.TODAY_TASK_NOT_FOUND);
        }

        DailyTask task = new DailyTask();
        task.setUserId(userId);
        task.setPlanId(plan.getId());
        task.setTaskDate(today);
        task.setGroupNo(nextGroupNo(userId, plan.getId(), today, DailyTaskType.DAILY));
        task.setTaskType(DailyTaskType.DAILY);
        task.setStatus(DailyTaskStatus.PENDING);
        task.setNewCount(newWords.size());
        task.setReviewCount(dueReviewStates.size());
        task.setExtraCount(0);
        task.setDoneCount(0);
        task.setSkippedCount(0);
        task.setDeleted(0);
        dailyTaskMapper.insert(task);

        insertReviewItems(task, dueReviewStates, 0);

        for (WordPickRow row : newWords) {
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
            dailyTaskItemMapper.insert(item);
        }

        if (!newWords.isEmpty()) {
            int maxSequenceNo = newWords.stream()
                    .map(WordPickRow::sequenceNo)
                    .max(Integer::compareTo)
                    .orElse(plan.getCurrentSequenceNo());
            plan.setCurrentSequenceNo(maxSequenceNo);
            plan.setLearnedCount(plan.getLearnedCount() + newWords.size());
            studyPlanMapper.updateById(plan);
        }
        return task;
    }

    private DailyTask syncDueReviewItems(Long userId, StudyPlan plan, DailyTask task, LocalDate today) {
        Set<Long> existingWordIds = dailyTaskItemMapper.selectList(new LambdaQueryWrapper<DailyTaskItem>()
                        .eq(DailyTaskItem::getDailyTaskId, task.getId()))
                .stream()
                .map(DailyTaskItem::getWordId)
                .collect(Collectors.toCollection(HashSet::new));
        int reviewSlots = Math.max(0, reviewWordsPerGroup(plan) - countTaskItems(task.getId(), DailyTaskItemType.REVIEW));
        List<UserWordState> dueReviewStates = selectDueReviewStates(userId, plan, today, existingWordIds, reviewSlots);
        if (!dueReviewStates.isEmpty()) {
            insertReviewItems(task, dueReviewStates, countTaskItems(task.getId(), DailyTaskItemType.REVIEW));
        }
        task.setReviewCount(countTaskItems(task.getId(), DailyTaskItemType.REVIEW));
        task.setNewCount(countTaskItems(task.getId(), DailyTaskItemType.NEW));
        task.setExtraCount(countTaskItems(task.getId(), DailyTaskItemType.EXTRA));
        task.setDoneCount(countTaskItems(task.getId(), DailyTaskItemStatus.DONE));
        task.setStatus(task.getDoneCount() >= totalCount(task) && totalCount(task) > 0 ? DailyTaskStatus.DONE : DailyTaskStatus.PENDING);
        if (task.getStatus() == DailyTaskStatus.PENDING) {
            task.setCompletedAt(null);
        }
        dailyTaskMapper.updateById(task);
        return task;
    }

    private int nextGroupNo(Long userId, Long planId, LocalDate today, DailyTaskType taskType) {
        DailyTask latestTask = dailyTaskMapper.selectOne(new LambdaQueryWrapper<DailyTask>()
                .eq(DailyTask::getUserId, userId)
                .eq(DailyTask::getPlanId, planId)
                .eq(DailyTask::getTaskDate, today)
                .eq(DailyTask::getTaskType, taskType)
                .orderByDesc(DailyTask::getGroupNo)
                .orderByDesc(DailyTask::getId)
                .last("LIMIT 1"));
        return latestTask == null || latestTask.getGroupNo() == null ? 1 : latestTask.getGroupNo() + 1;
    }

    private List<UserWordState> selectDueReviewStates(Long userId, StudyPlan plan, LocalDate today, Set<Long> excludedWordIds, int limit) {
        if (limit <= 0) {
            return Collections.emptyList();
        }
        return userWordStateMapper.selectList(new LambdaQueryWrapper<UserWordState>()
                .eq(UserWordState::getUserId, userId)
                .eq(UserWordState::getWordbookId, plan.getWordbookId())
                .eq(UserWordState::getLearned, true)
                .isNotNull(UserWordState::getNextReviewDate)
                .le(UserWordState::getNextReviewDate, today)
                .notIn(excludedWordIds != null && !excludedWordIds.isEmpty(), UserWordState::getWordId, excludedWordIds)
                .orderByAsc(UserWordState::getNextReviewDate)
                .orderByDesc(UserWordState::getWrongCount)
                .orderByAsc(UserWordState::getUpdatedAt)
                .orderByAsc(UserWordState::getId)
                .last("LIMIT " + limit));
    }

    private void insertReviewItems(DailyTask task, List<UserWordState> dueReviewStates, int startIndex) {
        int index = startIndex;
        for (UserWordState state : dueReviewStates) {
            DailyTaskItem item = new DailyTaskItem();
            item.setDailyTaskId(task.getId());
            item.setUserId(task.getUserId());
            item.setPlanId(task.getPlanId());
            item.setWordbookId(state.getWordbookId());
            item.setWordId(state.getWordId());
            item.setItemType(DailyTaskItemType.REVIEW);
            item.setStatus(DailyTaskItemStatus.PENDING);
            item.setSequenceNo(REVIEW_SEQUENCE_BASE + index++);
            item.setDeleted(0);
            dailyTaskItemMapper.insert(item);
        }
    }

    private void insertExtraItems(DailyTask task, List<WrongWord> wrongWords, int startIndex) {
        int index = startIndex;
        for (WrongWord wrongWord : wrongWords) {
            DailyTaskItem item = new DailyTaskItem();
            item.setDailyTaskId(task.getId());
            item.setUserId(task.getUserId());
            item.setPlanId(task.getPlanId());
            item.setWordbookId(wrongWord.getWordbookId());
            item.setWordId(wrongWord.getWordId());
            item.setItemType(DailyTaskItemType.EXTRA);
            item.setStatus(DailyTaskItemStatus.PENDING);
            item.setSequenceNo(EXTRA_SEQUENCE_BASE + index++);
            item.setDeleted(0);
            dailyTaskItemMapper.insert(item);
        }
    }

    private DailyTaskResponse toResponse(DailyTask task, StudyPlan plan) {
        Wordbook wordbook = wordbookService.getEnabledWordbook(plan.getWordbookId());
        List<DailyTaskItem> items = dailyTaskItemMapper.selectList(new LambdaQueryWrapper<DailyTaskItem>()
                .eq(DailyTaskItem::getDailyTaskId, task.getId())
                .orderByAsc(DailyTaskItem::getSequenceNo)
                .orderByAsc(DailyTaskItem::getId));
        List<DailyTaskItemResponse> itemResponses = buildItemResponses(items);
        DailyTaskPlanResponse planResponse = new DailyTaskPlanResponse(String.valueOf(plan.getId()), wordbook.getName());
        Long clozeQuizId = latestClozeQuizId(task.getId());
        boolean clozeGenerated = clozeQuizId != null;
        boolean clozeAttempted = hasCompletedGroupClozeAttempt(task);
        return DailyTaskResponse.from(task, planResponse, itemResponses, clozeGenerated, clozeAttempted, clozeQuizId);
    }

    private Long latestClozeQuizId(Long dailyTaskId) {
        ClozeQuiz quiz = clozeQuizMapper.selectOne(new LambdaQueryWrapper<ClozeQuiz>()
                .eq(ClozeQuiz::getDailyTaskId, dailyTaskId)
                .orderByDesc(ClozeQuiz::getId)
                .last("LIMIT 1"));
        return quiz == null ? null : quiz.getId();
    }

    private boolean hasClozeAttempt(Long userId, Long quizId) {
        return clozeAttemptMapper.selectCount(new LambdaQueryWrapper<ClozeAttempt>()
                .eq(ClozeAttempt::getUserId, userId)
                .eq(ClozeAttempt::getQuizId, quizId)) > 0;
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

    private List<Word> selectDailyTaskWords(Long dailyTaskId) {
        if (dailyTaskId == null) {
            return Collections.emptyList();
        }
        List<DailyTaskItem> items = dailyTaskItemMapper.selectList(new LambdaQueryWrapper<DailyTaskItem>()
                .eq(DailyTaskItem::getDailyTaskId, dailyTaskId)
                .orderByAsc(DailyTaskItem::getSequenceNo)
                .orderByAsc(DailyTaskItem::getId));
        if (items.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> wordIds = items.stream()
                .map(DailyTaskItem::getWordId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (wordIds.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, Word> wordMap = wordMapper.selectBatchIds(wordIds).stream()
                .collect(Collectors.toMap(Word::getId, Function.identity(), (first, ignored) -> first));
        return wordIds.stream()
                .map(wordMap::get)
                .filter(Objects::nonNull)
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

    private DailyTask getOwnedTask(Long userId, Long taskId) {
        DailyTask task = dailyTaskMapper.selectOne(new LambdaQueryWrapper<DailyTask>()
                .eq(DailyTask::getId, taskId)
                .eq(DailyTask::getUserId, userId)
                .last("LIMIT 1"));
        if (task == null) {
            throw new BizException(ErrorCode.TODAY_TASK_NOT_FOUND);
        }
        return task;
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

    private FavoriteWord findFavoriteWord(Long userId, Long wordbookId, Long wordId) {
        return favoriteWordMapper.selectOne(new LambdaQueryWrapper<FavoriteWord>()
                .eq(FavoriteWord::getUserId, userId)
                .eq(FavoriteWord::getWordbookId, wordbookId)
                .eq(FavoriteWord::getWordId, wordId)
                .last("LIMIT 1"));
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

    private void resolveWrongWord(Long userId, DailyTaskItem item) {
        WrongWord wrongWord = wrongWordMapper.selectOne(new LambdaQueryWrapper<WrongWord>()
                .eq(WrongWord::getUserId, userId)
                .eq(WrongWord::getWordbookId, item.getWordbookId())
                .eq(WrongWord::getWordId, item.getWordId())
                .eq(WrongWord::getResolved, false)
                .last("LIMIT 1"));
        if (wrongWord == null) {
            return;
        }
        wrongWord.setResolved(true);
        wrongWord.setResolvedAt(LocalDateTime.now());
        wrongWordMapper.updateById(wrongWord);
    }

    private DailyTask updateDailyTaskProgress(Long dailyTaskId) {
        DailyTask task = dailyTaskMapper.selectById(dailyTaskId);
        if (task == null) {
            throw new BizException(ErrorCode.TODAY_TASK_NOT_FOUND);
        }
        DailyTaskStatus previousStatus = task.getStatus();
        task.setNewCount(countTaskItems(dailyTaskId, DailyTaskItemType.NEW));
        task.setReviewCount(countTaskItems(dailyTaskId, DailyTaskItemType.REVIEW));
        task.setExtraCount(countTaskItems(dailyTaskId, DailyTaskItemType.EXTRA));
        task.setDoneCount(countTaskItems(dailyTaskId, DailyTaskItemStatus.DONE));
        if (task.getDoneCount() >= totalCount(task) && totalCount(task) > 0) {
            task.setStatus(DailyTaskStatus.DONE);
            task.setCompletedAt(LocalDateTime.now());
        } else {
            task.setStatus(DailyTaskStatus.PENDING);
            task.setCompletedAt(null);
        }
        dailyTaskMapper.updateById(task);
        if (previousStatus != DailyTaskStatus.DONE
                && task.getStatus() == DailyTaskStatus.DONE
                && task.getTaskType() == DailyTaskType.DAILY) {
            clozeQuizService.prefetchCompletedGroupCloze(task.getUserId(), task.getId());
        }
        return task;
    }

    private int countTaskItems(Long dailyTaskId, DailyTaskItemType itemType) {
        return dailyTaskItemMapper.selectCount(new LambdaQueryWrapper<DailyTaskItem>()
                .eq(DailyTaskItem::getDailyTaskId, dailyTaskId)
                .eq(DailyTaskItem::getItemType, itemType)).intValue();
    }

    private int countTaskItems(Long dailyTaskId, DailyTaskItemStatus status) {
        return dailyTaskItemMapper.selectCount(new LambdaQueryWrapper<DailyTaskItem>()
                .eq(DailyTaskItem::getDailyTaskId, dailyTaskId)
                .eq(DailyTaskItem::getStatus, status)).intValue();
    }

    private void updateStudyPlanProgress(DailyTaskItem item, StudyScene scene, SpacedRepetitionResult repetitionResult, boolean completed) {
        StudyPlan plan = studyPlanMapper.selectById(item.getPlanId());
        if (plan == null) {
            throw new BizException(ErrorCode.STUDY_PLAN_NOT_FOUND);
        }
        if (completed && scene == StudyScene.REVIEW) {
            plan.setReviewedCount(plan.getReviewedCount() + 1);
        }
        if (repetitionResult.oldMasteryStatus() != MasteryStatus.MASTERED && repetitionResult.newMasteryStatus() == MasteryStatus.MASTERED) {
            plan.setMasteredCount(plan.getMasteredCount() + 1);
        } else if (repetitionResult.oldMasteryStatus() == MasteryStatus.MASTERED && repetitionResult.newMasteryStatus() != MasteryStatus.MASTERED) {
            plan.setMasteredCount(Math.max(0, plan.getMasteredCount() - 1));
        }
        studyPlanMapper.updateById(plan);
    }

    private int totalCount(DailyTask task) {
        return safeCount(task.getNewCount()) + safeCount(task.getReviewCount()) + safeCount(task.getExtraCount());
    }

    private int safeCount(Integer count) {
        return count == null ? 0 : count;
    }

    private int newWordsPerGroup(StudyPlan plan) {
        return plan.getNewWordsPerGroup() == null ? 20 : plan.getNewWordsPerGroup();
    }

    private int reviewWordsPerGroup(StudyPlan plan) {
        return plan.getReviewWordsPerGroup() == null ? newWordsPerGroup(plan) * 2 : plan.getReviewWordsPerGroup();
    }

    private StudyScene toStudyScene(DailyTaskItemType itemType) {
        return switch (itemType) {
            case NEW -> StudyScene.NEW;
            case REVIEW -> StudyScene.REVIEW;
            case EXTRA -> StudyScene.EXTRA;
        };
    }

}
