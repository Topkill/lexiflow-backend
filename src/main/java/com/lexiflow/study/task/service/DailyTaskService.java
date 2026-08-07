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

/**
 * 每日学习任务服务。
 * <p>管理每日学习任务的完整生命周期，包括：
 * <ul>
 *   <li>任务生成：根据学习计划配置自动生成包含 NEW/REVIEW/EXTRA 三类任务项的每日任务</li>
 *   <li>任务查询：查询今日任务、指定任务和学习卡片详情</li>
 *   <li>反馈处理：接收用户的认识/不认识反馈，更新单词状态、错词记录和学习计划进度</li>
 *   <li>错词练习：创建错词专项复习任务</li>
 *   <li>完形填空：任务完成后异步触发完形填空题生成</li>
 * </ul>
 * </p>
 */
@Service
@RequiredArgsConstructor
public class DailyTaskService {

    /** REVIEW 类型任务项的序号基准值（负数确保复习项排在新学项之前） */
    private static final int REVIEW_SEQUENCE_BASE = -100_000;
    /** EXTRA 类型任务项的序号基准值（大正数确保加练项排在最后） */
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

    /**
     * 查询今日任务。
     * <p>优先返回已完成但尚未完成完形填空测验的任务，
     * 否则查询待处理任务（若过期则同步到期复习项），
     * 若不存在则自动生成今日任务。</p>
     *
     * @param userId 用户 ID
     * @return 今日任务响应
     */
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

    /** 根据任务 ID 查询任务详情。 */
    public DailyTaskResponse getTask(Long userId, Long taskId) {
        DailyTask task = getOwnedTask(userId, taskId);
        StudyPlan plan = studyPlanMapper.selectById(task.getPlanId());
        if (plan == null || !plan.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.STUDY_PLAN_NOT_FOUND);
        }
        return toResponse(task, plan);
    }

    /** 获取学习卡片详情，包含单词信息、收藏状态、掌握度和选择题。 */
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

    /** 创建错词专项复习任务，从用户未解决的错词中按错误次数降序选取。 */
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

    /**
     * 提交单词学习反馈。
     * <p>处理认识/不认识反馈，更新单词状态、错词记录和学习计划进度。
     * 对于错词练习任务使用独立的反馈处理逻辑。</p>
     *
     * @param userId  用户 ID
     * @param itemId  任务项 ID
     * @param request 反馈请求
     * @return 反馈响应，包含下次复习日期和任务进度
     */
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

    /** 处理错词练习任务的反馈，认识则标记错词为已解决，不认识则更新错词记录。 */
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

    /** 幂等性领取反馈提交，通过 CAS 更新确保同一任务项不被重复提交。 */
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

    /** 构建已应用反馈的响应（幂等性返回当前状态）。 */
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

    /** 判断反馈是否已经被应用过。 */
    private boolean isAlreadyAppliedFeedback(DailyTaskItem item, StudyFeedback feedback) {
        if (!feedback.name().equals(item.getFeedback())) {
            return false;
        }
        if (feedback == StudyFeedback.KNOWN) {
            return item.getStatus() == DailyTaskItemStatus.DONE;
        }
        return item.getStatus() == DailyTaskItemStatus.PENDING;
    }

    /** 递增已完成计数，若任务全部完成则标记为 DONE 并触发完形填空生成。 */
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

    /** 若任务已完成所有任务项，则标记任务状态为 DONE。 */
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

    /** 根据 ID 查询任务，不存在则抛出异常。 */
    private DailyTask getDailyTaskForProgress(Long dailyTaskId) {
        DailyTask task = dailyTaskMapper.selectById(dailyTaskId);
        if (task == null) {
            throw new BizException(ErrorCode.TODAY_TASK_NOT_FOUND);
        }
        return task;
    }

    /** 根据反馈类型返回对应的质量评分。 */
    private int qualityScore(StudyFeedback feedback) {
        return switch (feedback) {
            case UNKNOWN -> 2;
            case KNOWN -> 4;
        };
    }

    /** 查询用户最新的待处理每日任务。 */
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

    /** 将未触碰的过期待处理任务滚动到今天。 */
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

    /** 判断是否需要同步到期复习项（任务过期且未被触碰，或任务已是今天）。 */
    private boolean shouldSyncDueReviewItems(DailyTask task, LocalDate today) {
        if (task.getTaskDate() == null || !task.getTaskDate().isBefore(today)) {
            return true;
        }
        return !hasTouchedTaskItems(task.getId());
    }

    /** 检查任务项是否已被用户触碰（有已完成项或有反馈记录）。 */
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

    /** 查找今天已完成但尚未完成完形填空测验的任务。 */
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

    /** 检查任务是否已有完形填空测验的作答记录。 */
    private boolean hasCompletedGroupClozeAttempt(DailyTask task) {
        List<ClozeQuiz> quizzes = clozeQuizMapper.selectList(new LambdaQueryWrapper<ClozeQuiz>()
                .eq(ClozeQuiz::getDailyTaskId, task.getId())
                .orderByDesc(ClozeQuiz::getId));
        if (quizzes.isEmpty()) {
            return false;
        }
        return quizzes.stream().anyMatch(quiz -> hasClozeAttempt(task.getUserId(), quiz.getId()));
    }

    /**
     * 生成今日学习任务。
     * <p>
     * 根据学习计划配置，查询到期的复习单词和新学单词候选，创建一条新的每日任务记录及其任务项。
     * 若无可用单词（既无到期复习词也无新学词），则抛出业务异常。
     * <p>
     * 生成流程：
     * <ol>
     *   <li>查询到期复习单词列表（根据间隔重复算法的 nextReviewDate）</li>
     *   <li>查询新学单词候选（从词书中按序号选取未学过的单词）</li>
     *   <li>创建 DailyTask 记录，设置 newCount / reviewCount 等统计字段</li>
     *   <li>为每个复习单词插入 REVIEW 类型的任务项</li>
     *   <li>为每个新学单词插入 NEW 类型的任务项</li>
     *   <li>更新学习计划的 currentSequenceNo 和 learnedCount</li>
     * </ol>
     *
     * @param userId 用户ID
     * @param plan   当前激活的学习计划
     * @param today  今天的日期
     * @return 新生成的每日任务
     * @throws BizException 当无可用单词时抛出 TODAY_TASK_NOT_FOUND
     */
    private DailyTask generateTodayTask(Long userId, StudyPlan plan, LocalDate today) {
        // 查询到期复习单词状态列表
        List<UserWordState> dueReviewStates = selectDueReviewStates(userId, plan, today, Set.of(), reviewWordsPerGroup(plan));
        // 查询新学单词候选（从词书中按序号选取未学过的单词）
        List<WordPickRow> newWords = wordMapper.selectNewWordCandidates(
                plan.getWordbookId(),
                plan.getCurrentSequenceNo(),
                newWordsPerGroup(plan)
        );
        // 查询未解决的错词，用于 EXTRA 加练
        List<WrongWord> wrongWords = selectUnresolvedWrongWords(userId, plan.getWordbookId(), newWordsPerGroup(plan));

        if (dueReviewStates.isEmpty() && newWords.isEmpty() && wrongWords.isEmpty()) {
            throw new BizException(ErrorCode.TODAY_TASK_NOT_FOUND);
        }

        // 创建每日任务记录
        DailyTask task = new DailyTask();
        task.setUserId(userId);
        task.setPlanId(plan.getId());
        task.setTaskDate(today);
        task.setGroupNo(nextGroupNo(userId, plan.getId(), today, DailyTaskType.DAILY));
        task.setTaskType(DailyTaskType.DAILY);
        task.setStatus(DailyTaskStatus.PENDING);
        task.setNewCount(newWords.size());
        task.setReviewCount(dueReviewStates.size());
        task.setExtraCount(wrongWords.size());
        task.setDoneCount(0);
        task.setSkippedCount(0);
        task.setDeleted(0);
        dailyTaskMapper.insert(task);

        // 插入复习单词任务项
        insertReviewItems(task, dueReviewStates, 0);

        // 插入新学单词任务项
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

        // 插入错词加练任务项（EXTRA）
        insertExtraItems(task, wrongWords, 0);

        // 更新学习计划的进度：当前序号和已学单词数
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

    /** 同步到期复习项到现有任务，更新任务统计字段。 */
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

    /** 计算指定用户、计划、日期和类型的下一个分组编号。 */
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

    /** 查询到期复习的单词状态列表，按复习日期升序、错词数降序排列。 */
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

    /**
     * 查询用户在该词书下未解决的错词列表，按错误次数降序排列。
     *
     * @param userId     用户ID
     * @param wordbookId 词书ID
     * @param limit      最大返回数量
     * @return 未解决的错词列表
     */
    private List<WrongWord> selectUnresolvedWrongWords(Long userId, Long wordbookId, int limit) {
        if (limit <= 0) {
            return Collections.emptyList();
        }
        return wrongWordMapper.selectList(new LambdaQueryWrapper<WrongWord>()
                .eq(WrongWord::getUserId, userId)
                .eq(WrongWord::getWordbookId, wordbookId)
                .eq(WrongWord::getResolved, false)
                .orderByDesc(WrongWord::getWrongCount)
                .orderByDesc(WrongWord::getLastWrongAt)
                .orderByAsc(WrongWord::getId)
                .last("LIMIT " + limit));
    }

    /** 插入复习单词任务项，序号从基准值开始递增。 */
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

    /** 插入错词加练任务项，序号从 EXTRA 基准值开始递增。 */
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

    /** 构建任务响应，包含任务项列表和完形填空状态。 */
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

    /** 查询任务关联的最新完形填空测验 ID。 */
    private Long latestClozeQuizId(Long dailyTaskId) {
        ClozeQuiz quiz = clozeQuizMapper.selectOne(new LambdaQueryWrapper<ClozeQuiz>()
                .eq(ClozeQuiz::getDailyTaskId, dailyTaskId)
                .orderByDesc(ClozeQuiz::getId)
                .last("LIMIT 1"));
        return quiz == null ? null : quiz.getId();
    }

    /** 检查用户是否已有指定测验的作答记录。 */
    private boolean hasClozeAttempt(Long userId, Long quizId) {
        return clozeAttemptMapper.selectCount(new LambdaQueryWrapper<ClozeAttempt>()
                .eq(ClozeAttempt::getUserId, userId)
                .eq(ClozeAttempt::getQuizId, quizId)) > 0;
    }

    /** 批量查询单词信息并构建任务项响应列表。 */
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

    /** 查询任务中所有任务项对应的单词列表。 */
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

    /** 查询用户拥有的任务项，不存在则抛出异常。 */
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

    /** 查询用户拥有的任务，不存在则抛出异常。 */
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

    /** 根据 ID 查询单词，不存在则抛出异常。 */
    private Word getWord(Long wordId) {
        Word word = wordMapper.selectById(wordId);
        if (word == null) {
            throw new BizException(ErrorCode.WORD_NOT_FOUND);
        }
        return word;
    }

    /** 查询用户对指定单词的学习状态。 */
    private UserWordState findUserWordState(Long userId, Long wordbookId, Long wordId) {
        return userWordStateMapper.selectOne(new LambdaQueryWrapper<UserWordState>()
                .eq(UserWordState::getUserId, userId)
                .eq(UserWordState::getWordbookId, wordbookId)
                .eq(UserWordState::getWordId, wordId)
                .last("LIMIT 1"));
    }

    /** 查询用户的收藏单词记录。 */
    private FavoriteWord findFavoriteWord(Long userId, Long wordbookId, Long wordId) {
        return favoriteWordMapper.selectOne(new LambdaQueryWrapper<FavoriteWord>()
                .eq(FavoriteWord::getUserId, userId)
                .eq(FavoriteWord::getWordbookId, wordbookId)
                .eq(FavoriteWord::getWordId, wordId)
                .last("LIMIT 1"));
    }

    /** 创建学习事件记录。 */
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

    /** 新增或更新错词记录，累加错误次数并重置解决状态。 */
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

    /** 将错词标记为已解决。 */
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

    /** 重新计算并更新任务的进度统计，任务完成时触发完形填空生成。 */
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

    /** 统计指定类型的任务项数量。 */
    private int countTaskItems(Long dailyTaskId, DailyTaskItemType itemType) {
        return dailyTaskItemMapper.selectCount(new LambdaQueryWrapper<DailyTaskItem>()
                .eq(DailyTaskItem::getDailyTaskId, dailyTaskId)
                .eq(DailyTaskItem::getItemType, itemType)).intValue();
    }

    /** 统计指定状态的任务项数量。 */
    private int countTaskItems(Long dailyTaskId, DailyTaskItemStatus status) {
        return dailyTaskItemMapper.selectCount(new LambdaQueryWrapper<DailyTaskItem>()
                .eq(DailyTaskItem::getDailyTaskId, dailyTaskId)
                .eq(DailyTaskItem::getStatus, status)).intValue();
    }

    /** 更新学习计划的进度统计（复习数、精通数）。 */
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

    /** 计算任务总项数。 */
    private int totalCount(DailyTask task) {
        return safeCount(task.getNewCount()) + safeCount(task.getReviewCount()) + safeCount(task.getExtraCount());
    }

    /** 空安全的计数值转换。 */
    private int safeCount(Integer count) {
        return count == null ? 0 : count;
    }

    /** 获取每组新学单词数，默认 20。 */
    private int newWordsPerGroup(StudyPlan plan) {
        return plan.getNewWordsPerGroup() == null ? 20 : plan.getNewWordsPerGroup();
    }

    /** 获取每组复习单词数，默认为新学单词数的 2 倍。 */
    private int reviewWordsPerGroup(StudyPlan plan) {
        return plan.getReviewWordsPerGroup() == null ? newWordsPerGroup(plan) * 2 : plan.getReviewWordsPerGroup();
    }

    /** 将任务项类型转换为学习场景。 */
    private StudyScene toStudyScene(DailyTaskItemType itemType) {
        return switch (itemType) {
            case NEW -> StudyScene.NEW;
            case REVIEW -> StudyScene.REVIEW;
            case EXTRA -> StudyScene.EXTRA;
        };
    }

}
