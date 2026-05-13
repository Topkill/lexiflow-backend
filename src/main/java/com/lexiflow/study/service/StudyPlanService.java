package com.lexiflow.study.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.lexiflow.common.error.ErrorCode;
import com.lexiflow.common.exception.BizException;
import com.lexiflow.study.domain.StudyPlan;
import com.lexiflow.study.domain.StudyPlanStatus;
import com.lexiflow.study.dto.CreateStudyPlanRequest;
import com.lexiflow.study.dto.StudyPlanResponse;
import com.lexiflow.study.mapper.StudyPlanMapper;
import com.lexiflow.wordbook.domain.Wordbook;
import com.lexiflow.wordbook.service.WordbookService;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StudyPlanService {

    private final StudyPlanMapper studyPlanMapper;
    private final WordbookService wordbookService;

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
        plan.setDailyNewWords(request.dailyNewWords());
        plan.setStartDate(request.startDate());
        plan.setExpectedFinishDate(calculateExpectedFinishDate(request.startDate(), wordbook.getWordCount(), request.dailyNewWords()));
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

    private LocalDate calculateExpectedFinishDate(LocalDate startDate, Integer totalWords, Integer dailyNewWords) {
        int safeTotal = totalWords == null ? 0 : totalWords;
        if (safeTotal <= 0) {
            return startDate;
        }
        int days = (safeTotal + dailyNewWords - 1) / dailyNewWords;
        return startDate.plusDays(days - 1L);
    }
}
