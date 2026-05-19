package com.lexiflow.study.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.lexiflow.common.error.ErrorCode;
import com.lexiflow.common.exception.BizException;
import com.lexiflow.study.domain.StudyPlan;
import com.lexiflow.study.domain.StudyPlanStatus;
import com.lexiflow.study.dto.CreateStudyPlanRequest;
import com.lexiflow.study.dto.StudyPlanResponse;
import com.lexiflow.study.dto.UpdateStudyPlanRequest;
import com.lexiflow.study.mapper.StudyPlanMapper;
import com.lexiflow.study.progress.domain.UserWordState;
import com.lexiflow.study.progress.mapper.UserWordStateMapper;
import com.lexiflow.study.task.domain.DailyTask;
import com.lexiflow.study.task.domain.DailyTaskItem;
import com.lexiflow.study.task.domain.DailyTaskItemStatus;
import com.lexiflow.study.task.domain.DailyTaskItemType;
import com.lexiflow.study.task.domain.DailyTaskStatus;
import com.lexiflow.study.task.mapper.DailyTaskItemMapper;
import com.lexiflow.study.task.mapper.DailyTaskMapper;
import com.lexiflow.wordbook.dto.WordPickRow;
import com.lexiflow.wordbook.mapper.WordMapper;
import com.lexiflow.wordbook.domain.Wordbook;
import com.lexiflow.wordbook.service.WordbookService;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StudyPlanService {

    private static final int REVIEW_SEQUENCE_BASE = -100_000;

    private final StudyPlanMapper studyPlanMapper;
    private final WordbookService wordbookService;
    private final DailyTaskMapper dailyTaskMapper;
    private final DailyTaskItemMapper dailyTaskItemMapper;
    private final UserWordStateMapper userWordStateMapper;
    private final WordMapper wordMapper;

    @Transactional
    public StudyPlanResponse createPlan(Long userId, CreateStudyPlanRequest request) {
        Wordbook wordbook = wordbookService.getEnabledWordbook(request.wordbookId());
        boolean primary = request.primaryOrDefault();
        if (primary) {
            endExistingPrimaryPlans(userId);
        }

        StudyPlan plan = new StudyPlan();
        plan.setUserId(userId);
        plan.setWordbookId(wordbook.getId());
        plan.setName(request.name().trim());
        plan.setNewWordsPerGroup(request.newWordsPerGroup());
        plan.setReviewWordsPerGroup(request.reviewWordsPerGroup());
        plan.setStartDate(request.startDate());
        plan.setExpectedFinishDate(calculateExpectedFinishDate(request.startDate(), wordbook.getWordCount(), request.newWordsPerGroup()));
        plan.setStatus(StudyPlanStatus.ACTIVE);
        plan.setTotalWords(wordbook.getWordCount());
        plan.setLearnedCount(0);
        plan.setReviewedCount(0);
        plan.setMasteredCount(0);
        plan.setCurrentSequenceNo(0);
        plan.setIsPrimary(primary);
        plan.setDeleted(0);
        plan.setVersion(0);
        studyPlanMapper.insert(plan);

        return StudyPlanResponse.from(plan, wordbook);
    }

    public StudyPlanResponse getPrimaryPlan(Long userId) {
        StudyPlan plan = findPrimaryPlan(userId);
        if (plan == null) {
            throw new BizException(ErrorCode.STUDY_PLAN_NOT_FOUND);
        }
        return toResponse(plan);
    }

    public StudyPlan getPrimaryActivePlanEntity(Long userId) {
        StudyPlan plan = studyPlanMapper.selectOne(new LambdaQueryWrapper<StudyPlan>()
                .eq(StudyPlan::getUserId, userId)
                .eq(StudyPlan::getIsPrimary, true)
                .eq(StudyPlan::getStatus, StudyPlanStatus.ACTIVE)
                .orderByDesc(StudyPlan::getCreatedAt)
                .last("LIMIT 1"));
        if (plan == null) {
            throw new BizException(ErrorCode.STUDY_PLAN_NOT_FOUND);
        }
        return plan;
    }

    @Transactional
    public StudyPlanResponse updatePlan(Long userId, Long planId, UpdateStudyPlanRequest request) {
        StudyPlan plan = getOwnedPlan(userId, planId);
        if (plan.getStatus() != StudyPlanStatus.ACTIVE && plan.getStatus() != StudyPlanStatus.PAUSED) {
            throw new BizException(ErrorCode.STUDY_PLAN_STATUS_INVALID);
        }
        plan.setName(request.name().trim());
        plan.setNewWordsPerGroup(request.newWordsPerGroup());
        plan.setReviewWordsPerGroup(request.reviewWordsPerGroup());
        plan.setExpectedFinishDate(calculateExpectedFinishDate(plan.getStartDate(), plan.getTotalWords(), request.newWordsPerGroup()));
        studyPlanMapper.updateById(plan);
        replanLatestPendingTask(userId, plan);
        return toResponse(plan);
    }

    @Transactional
    public StudyPlanResponse pausePlan(Long userId, Long planId) {
        StudyPlan plan = getOwnedPlan(userId, planId);
        if (plan.getStatus() != StudyPlanStatus.ACTIVE) {
            throw new BizException(ErrorCode.STUDY_PLAN_STATUS_INVALID);
        }
        plan.setStatus(StudyPlanStatus.PAUSED);
        studyPlanMapper.updateById(plan);
        return toResponse(plan);
    }

    @Transactional
    public StudyPlanResponse resumePlan(Long userId, Long planId) {
        StudyPlan plan = getOwnedPlan(userId, planId);
        if (plan.getStatus() != StudyPlanStatus.PAUSED) {
            throw new BizException(ErrorCode.STUDY_PLAN_STATUS_INVALID);
        }
        plan.setStatus(StudyPlanStatus.ACTIVE);
        studyPlanMapper.updateById(plan);
        return toResponse(plan);
    }

    @Transactional
    public StudyPlanResponse endPlan(Long userId, Long planId) {
        StudyPlan plan = getOwnedPlan(userId, planId);
        if (plan.getStatus() != StudyPlanStatus.ACTIVE && plan.getStatus() != StudyPlanStatus.PAUSED) {
            throw new BizException(ErrorCode.STUDY_PLAN_STATUS_INVALID);
        }
        plan.setStatus(StudyPlanStatus.ENDED);
        plan.setActualFinishDate(LocalDate.now());
        studyPlanMapper.updateById(plan);
        return toResponse(plan);
    }

    private void endExistingPrimaryPlans(Long userId) {
        StudyPlan update = new StudyPlan();
        update.setStatus(StudyPlanStatus.ENDED);
        update.setActualFinishDate(LocalDate.now());
        studyPlanMapper.update(update, new LambdaUpdateWrapper<StudyPlan>()
                .eq(StudyPlan::getUserId, userId)
                .eq(StudyPlan::getIsPrimary, true)
                .in(StudyPlan::getStatus, StudyPlanStatus.ACTIVE, StudyPlanStatus.PAUSED));
    }

    private StudyPlan findPrimaryPlan(Long userId) {
        return studyPlanMapper.selectOne(new LambdaQueryWrapper<StudyPlan>()
                .eq(StudyPlan::getUserId, userId)
                .eq(StudyPlan::getIsPrimary, true)
                .in(StudyPlan::getStatus, StudyPlanStatus.ACTIVE, StudyPlanStatus.PAUSED)
                .orderByDesc(StudyPlan::getCreatedAt)
                .last("LIMIT 1"));
    }

    private StudyPlan getOwnedPlan(Long userId, Long planId) {
        StudyPlan plan = studyPlanMapper.selectOne(new LambdaQueryWrapper<StudyPlan>()
                .eq(StudyPlan::getId, planId)
                .eq(StudyPlan::getUserId, userId)
                .last("LIMIT 1"));
        if (plan == null) {
            throw new BizException(ErrorCode.STUDY_PLAN_NOT_FOUND);
        }
        return plan;
    }

    private StudyPlanResponse toResponse(StudyPlan plan) {
        Wordbook wordbook = wordbookService.getEnabledWordbook(plan.getWordbookId());
        return StudyPlanResponse.from(plan, wordbook);
    }

    private void replanLatestPendingTask(Long userId, StudyPlan plan) {
        DailyTask task = dailyTaskMapper.selectOne(new LambdaQueryWrapper<DailyTask>()
                .eq(DailyTask::getUserId, userId)
                .eq(DailyTask::getPlanId, plan.getId())
                .eq(DailyTask::getStatus, DailyTaskStatus.PENDING)
                .orderByDesc(DailyTask::getTaskDate)
                .orderByDesc(DailyTask::getGroupNo)
                .orderByDesc(DailyTask::getId)
                .last("LIMIT 1"));
        if (task == null) {
            return;
        }

        List<DailyTaskItem> items = selectTaskItems(task.getId());
        adjustNewItems(userId, plan, task, items);
        items = selectTaskItems(task.getId());
        adjustReviewItems(userId, plan, task, items);
        refreshTaskAndPlanCounts(plan, task.getId());
    }

    private List<DailyTaskItem> selectTaskItems(Long dailyTaskId) {
        return dailyTaskItemMapper.selectList(new LambdaQueryWrapper<DailyTaskItem>()
                .eq(DailyTaskItem::getDailyTaskId, dailyTaskId)
                .orderByAsc(DailyTaskItem::getSequenceNo)
                .orderByAsc(DailyTaskItem::getId));
    }

    private void adjustNewItems(Long userId, StudyPlan plan, DailyTask task, List<DailyTaskItem> items) {
        List<DailyTaskItem> newItems = items.stream()
                .filter(item -> item.getItemType() == DailyTaskItemType.NEW)
                .toList();
        List<DailyTaskItem> removableItems = newItems.stream()
                .filter(this::isUntouchedPendingItem)
                .sorted(Comparator.comparing(DailyTaskItem::getSequenceNo).thenComparing(DailyTaskItem::getId))
                .toList();
        int lockedCount = newItems.size() - removableItems.size();
        int desiredRemovableCount = Math.max(0, plan.getNewWordsPerGroup() - lockedCount);
        if (removableItems.size() > desiredRemovableCount) {
            removableItems.stream()
                    .skip(desiredRemovableCount)
                    .forEach(item -> dailyTaskItemMapper.deleteById(item.getId()));
        } else if (removableItems.size() < desiredRemovableCount) {
            int missingCount = desiredRemovableCount - removableItems.size();
            List<WordPickRow> newWords = wordMapper.selectNewWordCandidates(
                    plan.getWordbookId(),
                    plan.getCurrentSequenceNo(),
                    missingCount
            );
            insertNewItems(userId, plan, task, newWords);
        }
    }

    private void adjustReviewItems(Long userId, StudyPlan plan, DailyTask task, List<DailyTaskItem> items) {
        List<DailyTaskItem> reviewItems = items.stream()
                .filter(item -> item.getItemType() == DailyTaskItemType.REVIEW)
                .toList();
        List<DailyTaskItem> removableItems = reviewItems.stream()
                .filter(this::isUntouchedPendingItem)
                .sorted(Comparator.comparing(DailyTaskItem::getSequenceNo).thenComparing(DailyTaskItem::getId))
                .toList();
        int lockedCount = reviewItems.size() - removableItems.size();
        int desiredRemovableCount = Math.max(0, plan.getReviewWordsPerGroup() - lockedCount);
        if (removableItems.size() > desiredRemovableCount) {
            removableItems.stream()
                    .skip(desiredRemovableCount)
                    .forEach(item -> dailyTaskItemMapper.deleteById(item.getId()));
        } else if (removableItems.size() < desiredRemovableCount) {
            Set<Long> existingWordIds = items.stream()
                    .map(DailyTaskItem::getWordId)
                    .collect(Collectors.toCollection(HashSet::new));
            List<UserWordState> dueReviewStates = selectDueReviewStates(
                    userId,
                    plan,
                    task.getTaskDate(),
                    existingWordIds,
                    desiredRemovableCount - removableItems.size()
            );
            insertReviewItems(task, dueReviewStates, reviewItems.size());
        }
    }

    private boolean isUntouchedPendingItem(DailyTaskItem item) {
        return item.getStatus() == DailyTaskItemStatus.PENDING && item.getFeedback() == null;
    }

    private void insertNewItems(Long userId, StudyPlan plan, DailyTask task, List<WordPickRow> newWords) {
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
    }

    private List<UserWordState> selectDueReviewStates(Long userId, StudyPlan plan, LocalDate today, Set<Long> excludedWordIds, int limit) {
        if (limit <= 0) {
            return List.of();
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

    private void refreshTaskAndPlanCounts(StudyPlan plan, Long dailyTaskId) {
        DailyTask task = dailyTaskMapper.selectById(dailyTaskId);
        if (task != null) {
            task.setNewCount(countTaskItems(dailyTaskId, DailyTaskItemType.NEW));
            task.setReviewCount(countTaskItems(dailyTaskId, DailyTaskItemType.REVIEW));
            task.setExtraCount(countTaskItems(dailyTaskId, DailyTaskItemType.EXTRA));
            task.setDoneCount(countTaskItems(dailyTaskId, DailyTaskItemStatus.DONE));
            dailyTaskMapper.updateById(task);
        }
        Integer currentSequenceNo = maxGeneratedNewSequenceNo(plan.getId());
        plan.setCurrentSequenceNo(currentSequenceNo == null ? 0 : currentSequenceNo);
        plan.setLearnedCount(countPlanItems(plan.getId(), DailyTaskItemType.NEW));
        studyPlanMapper.updateById(plan);
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

    private int countPlanItems(Long planId, DailyTaskItemType itemType) {
        return dailyTaskItemMapper.selectCount(new LambdaQueryWrapper<DailyTaskItem>()
                .eq(DailyTaskItem::getPlanId, planId)
                .eq(DailyTaskItem::getItemType, itemType)).intValue();
    }

    private Integer maxGeneratedNewSequenceNo(Long planId) {
        DailyTaskItem item = dailyTaskItemMapper.selectOne(new LambdaQueryWrapper<DailyTaskItem>()
                .eq(DailyTaskItem::getPlanId, planId)
                .eq(DailyTaskItem::getItemType, DailyTaskItemType.NEW)
                .orderByDesc(DailyTaskItem::getSequenceNo)
                .last("LIMIT 1"));
        return item == null ? null : item.getSequenceNo();
    }

    private LocalDate calculateExpectedFinishDate(LocalDate startDate, Integer totalWords, Integer newWordsPerGroup) {
        int safeTotal = totalWords == null ? 0 : totalWords;
        if (safeTotal <= 0) {
            return startDate;
        }
        int safeGroupSize = newWordsPerGroup == null || newWordsPerGroup <= 0 ? 20 : newWordsPerGroup;
        int groups = (safeTotal + safeGroupSize - 1) / safeGroupSize;
        return startDate.plusDays(groups - 1L);
    }
}
