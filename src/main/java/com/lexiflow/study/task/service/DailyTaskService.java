package com.lexiflow.study.task.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lexiflow.common.error.ErrorCode;
import com.lexiflow.common.exception.BizException;
import com.lexiflow.study.domain.StudyPlan;
import com.lexiflow.study.mapper.StudyPlanMapper;
import com.lexiflow.study.service.StudyPlanService;
import com.lexiflow.study.task.domain.DailyTask;
import com.lexiflow.study.task.domain.DailyTaskItem;
import com.lexiflow.study.task.domain.DailyTaskItemStatus;
import com.lexiflow.study.task.domain.DailyTaskItemType;
import com.lexiflow.study.task.domain.DailyTaskStatus;
import com.lexiflow.study.task.dto.DailyTaskItemResponse;
import com.lexiflow.study.task.dto.DailyTaskPlanResponse;
import com.lexiflow.study.task.dto.DailyTaskResponse;
import com.lexiflow.study.task.mapper.DailyTaskItemMapper;
import com.lexiflow.study.task.mapper.DailyTaskMapper;
import com.lexiflow.wordbook.domain.Word;
import com.lexiflow.wordbook.domain.Wordbook;
import com.lexiflow.wordbook.dto.WordbookWordPickRow;
import com.lexiflow.wordbook.mapper.WordMapper;
import com.lexiflow.wordbook.mapper.WordbookWordMapper;
import com.lexiflow.wordbook.service.WordbookService;
import java.time.LocalDate;
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
}
