package com.lexiflow.study.progress.service;

import com.lexiflow.study.progress.domain.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/** 二元反馈的纯计算规则；每日额度与正式复习资格由事务服务判定。 */
final class ReviewSchedulingPolicy {
    private static final BigDecimal MIN_EF = new BigDecimal("1.30");
    private static final BigDecimal MAX_EF = new BigDecimal("2.70");
    private ReviewSchedulingPolicy() {}

    static boolean apply(UserWordState state, StudyFeedback feedback, AttemptType type,
                         LocalDate today, boolean firstUnknown, boolean formalAllowed) {
        if (feedback == StudyFeedback.UNKNOWN) {
            state.setWrongCount(state.getWrongCount() + 1);
            state.setMasteryStatus(state.getWrongCount() >= 3 ? MasteryStatus.DIFFICULT : MasteryStatus.LEARNING);
            if (!firstUnknown) return false;
            state.setEasinessFactor(state.getEasinessFactor().subtract(new BigDecimal("0.20")).max(MIN_EF).min(MAX_EF));
            state.setRepetition(0);
            schedule(state, today, 1);
            return true;
        }
        state.setCorrectCount(state.getCorrectCount() + 1);
        if (type == AttemptType.INITIAL_LEARNING && !Boolean.TRUE.equals(state.getLearned())) {
            state.setRepetition(1);
            state.setMasteryStatus(MasteryStatus.REVIEWING);
            schedule(state, today, 1);
            return true;
        }
        if (type != AttemptType.FORMAL_REVIEW || !formalAllowed) return false;
        BigDecimal ef = state.getEasinessFactor().add(new BigDecimal("0.05")).max(MIN_EF).min(MAX_EF);
        state.setEasinessFactor(ef);
        int repetition = state.getRepetition() + 1;
        state.setRepetition(repetition);
        int interval = repetition == 1 ? 1 : repetition == 2 ? 3
                : BigDecimal.valueOf(state.getIntervalDays()).multiply(ef)
                        .setScale(0, RoundingMode.HALF_UP)
                        .max(BigDecimal.ONE).min(BigDecimal.valueOf(365)).intValueExact();
        schedule(state, today, interval);
        state.setMasteryStatus(repetition >= 3 ? MasteryStatus.MASTERED : MasteryStatus.REVIEWING);
        return true;
    }

    private static void schedule(UserWordState state, LocalDate today, int interval) {
        state.setIntervalDays(interval);
        state.setNextReviewDate(today.plusDays(interval));
    }
}
