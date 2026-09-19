package com.lexiflow.study.progress.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lexiflow.study.progress.domain.*;
import com.lexiflow.study.progress.mapper.StudyDailyWordEffectMapper;
import com.lexiflow.study.progress.mapper.UserWordStateMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SpacedRepetitionService {
    private final UserWordStateMapper userWordStateMapper;
    private final StudyDailyWordEffectMapper dailyEffectMapper;
    private final StudyBusinessTime businessTime;

    /** 在反馈事务的第一次读取之前调用；数据库锁随外层事务提交或回滚释放。 */
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockUser(Long userId) {
        dailyEffectMapper.lockUser(userId);
    }

    @Transactional
    public SpacedRepetitionResult applyFeedback(Long userId, Long wordbookId, Long wordId, Long planId,
                                                StudyFeedback feedback, StudyScene scene, AttemptType requestedType) {
        dailyEffectMapper.lockUser(userId);
        LocalDateTime now = businessTime.now();
        LocalDate today = now.toLocalDate();
        dailyEffectMapper.ensureDay(userId, wordId, today);
        StudyDailyWordEffect day = dailyEffectMapper.lockDay(userId, wordId, today);
        UserWordState state = userWordStateMapper.selectOne(new LambdaQueryWrapper<UserWordState>()
                .eq(UserWordState::getUserId, userId).eq(UserWordState::getWordbookId, wordbookId)
                .eq(UserWordState::getWordId, wordId).last("LIMIT 1 FOR UPDATE"));
        boolean newState = state == null;
        if (newState) state = newState(userId, wordbookId, wordId, planId, today);
        MasteryStatus oldMastery = state.getMasteryStatus();
        boolean formalAllowed = scene == StudyScene.REVIEW
                && requestedType == AttemptType.FORMAL_REVIEW
                && Boolean.TRUE.equals(state.getLearned())
                && state.getLastStudiedAt() != null && state.getLastStudiedAt().toLocalDate().isBefore(today)
                && state.getNextReviewDate() != null && !state.getNextReviewDate().isAfter(today)
                && !day.getUnknownEfApplied() && !day.getKnownReviewApplied();
        AttemptType effectiveType = scene == StudyScene.QUIZ ? AttemptType.QUIZ
                : formalAllowed ? AttemptType.FORMAL_REVIEW
                : scene == StudyScene.NEW && requestedType == AttemptType.INITIAL_LEARNING
                  && !Boolean.TRUE.equals(state.getLearned()) && !day.getUnknownEfApplied()
                    ? AttemptType.INITIAL_LEARNING : AttemptType.IN_DAY_RETRY;
        boolean firstUnknown = feedback == StudyFeedback.UNKNOWN && !day.getUnknownEfApplied();
        boolean applied = ReviewSchedulingPolicy.apply(state, feedback, effectiveType, today, firstUnknown, formalAllowed);
        if (firstUnknown) day.setUnknownEfApplied(true);
        if (applied && feedback == StudyFeedback.KNOWN) day.setKnownReviewApplied(true);
        if (applied) {
            day.setUpdatedAt(now);
            dailyEffectMapper.updateById(day);
        }
        if (planId != null) state.setPlanId(planId);
        state.setLearned(true);
        state.setLastFeedback(feedback);
        state.setLastStudiedAt(now);
        if (scene == StudyScene.REVIEW) state.setLastReviewedAt(now);
        if (newState) userWordStateMapper.insert(state);
        else userWordStateMapper.updateById(state);
        return new SpacedRepetitionResult(state.getNextReviewDate(), qualityScore(feedback), oldMastery,
                state.getMasteryStatus(), effectiveType, today, applied, now);
    }

    public int qualityScore(StudyFeedback feedback) {
        return feedback == StudyFeedback.UNKNOWN ? 2 : 4;
    }

    private UserWordState newState(Long userId, Long wordbookId, Long wordId, Long planId, LocalDate today) {
        UserWordState state = new UserWordState();
        state.setUserId(userId);
        state.setWordbookId(wordbookId);
        state.setWordId(wordId);
        state.setPlanId(planId);
        state.setMasteryStatus(MasteryStatus.NEW);
        state.setLearned(false);
        state.setRepetition(0);
        state.setIntervalDays(1);
        state.setNextReviewDate(today.plusDays(1));
        state.setEasinessFactor(new BigDecimal("2.50"));
        state.setWrongCount(0);
        state.setCorrectCount(0);
        state.setDeleted(0);
        return state;
    }

    public record SpacedRepetitionResult(LocalDate nextReviewDate, int qualityScore,
            MasteryStatus oldMasteryStatus, MasteryStatus newMasteryStatus,
            AttemptType attemptType, LocalDate businessDate, boolean algorithmApplied, LocalDateTime occurredAt) {}
}
