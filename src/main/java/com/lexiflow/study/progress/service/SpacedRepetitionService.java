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
    /**
     * 调度算法版本：EF 上下限 1.30~2.70、间隔 1~365、DIFFICULT 按 EF≤1.70 判定并自然脱困。
     * <p>V2 内部包含两代间隔实现，版本号不再细分，做效果归因时以事件快照
     * （{@code interval_days_before/after} 等）为准：</p>
     * <ul>
     *   <li>2026-09-19 起：rep≥3 用 {@code 间隔 × EF}，HALF_UP，钳制 1~365；</li>
     *   <li>2026-09-21 起（提交 666100c）：rep≥3 改用 Anki Good 档逾期折半
     *       {@code (原间隔 + 逾期天数/2) × EF}，下限 {@code max(1, prev+1)} 防倒退，上限 365；
     *       同时业务日改为 Asia/Shanghai 凌晨 4 点偏移。</li>
     * </ul>
     */
    public static final String ALGORITHM_VERSION = "V2_BOUNDED_STEP";

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
        LocalDate today = businessTime.businessDate();
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
        BigDecimal efBefore = state.getEasinessFactor();
        Integer intervalBefore = state.getIntervalDays();
        Integer repetitionBefore = state.getRepetition();
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
                state.getMasteryStatus(), effectiveType, today, applied, now,
                efBefore, state.getEasinessFactor(),
                intervalBefore, state.getIntervalDays(),
                repetitionBefore, state.getRepetition(),
                ALGORITHM_VERSION);
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
            AttemptType attemptType, LocalDate businessDate, boolean algorithmApplied, LocalDateTime occurredAt,
            BigDecimal efBefore, BigDecimal efAfter,
            Integer intervalDaysBefore, Integer intervalDaysAfter,
            Integer repetitionBefore, Integer repetitionAfter,
            String algorithmVersion) {}
}
