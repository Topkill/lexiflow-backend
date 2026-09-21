package com.lexiflow.study.progress.service;

import com.lexiflow.study.progress.domain.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** 二元反馈的纯计算规则；每日额度与正式复习资格由事务服务判定。 */
final class ReviewSchedulingPolicy {
    private static final BigDecimal MIN_EF = new BigDecimal("1.30");
    private static final BigDecimal MAX_EF = new BigDecimal("2.70");
    /** DIFFICULT 的 EF 阈值：EF 是认知强度的动态画像，净失败 4 次（<=1.70）才算困难词。 */
    private static final BigDecimal DIFFICULT_EF_THRESHOLD = new BigDecimal("1.70");
    private ReviewSchedulingPolicy() {}

    static boolean apply(UserWordState state, StudyFeedback feedback, AttemptType type,
                         LocalDate today, boolean firstUnknown, boolean formalAllowed) {
        if (feedback == StudyFeedback.UNKNOWN) {
            // 历史失败次数仅作统计（排序、报表），不再参与状态判定
            state.setWrongCount(state.getWrongCount() + 1);
            // 当日第二次及以后的失败不再改变调度与状态，避免日内重练刷爆标签
            if (!firstUnknown) return false;
            BigDecimal nextEf = state.getEasinessFactor().subtract(new BigDecimal("0.20")).max(MIN_EF).min(MAX_EF);
            state.setEasinessFactor(nextEf);
            state.setRepetition(0);
            schedule(state, today, 1);
            state.setMasteryStatus(nextEf.compareTo(DIFFICULT_EF_THRESHOLD) <= 0
                    ? MasteryStatus.DIFFICULT : MasteryStatus.LEARNING);
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
        int interval;
        if (repetition == 1) {
            interval = 1;
        } else if (repetition == 2) {
            interval = 3;
        } else {
            // Anki Good 档逾期折半公式：(原间隔 + 逾期天数/2) × EF
            // 逾期答对按折半奖励基准；下限 prev+1 防止间隔倒退；上限 365 封顶
            int delay = 0;
            if (state.getNextReviewDate() != null && today.isAfter(state.getNextReviewDate())) {
                delay = (int) ChronoUnit.DAYS.between(state.getNextReviewDate(), today);
            }
            interval = BigDecimal.valueOf(state.getIntervalDays())
                    .add(BigDecimal.valueOf(delay).divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP))
                    .multiply(ef)
                    .setScale(0, RoundingMode.HALF_UP)
                    .max(BigDecimal.valueOf(state.getIntervalDays() + 1L))
                    .min(BigDecimal.valueOf(365))
                    .intValueExact();
        }
        schedule(state, today, interval);
        // 自然脱困法：MASTERED 判定保持不变；DIFFICULT 摘除以 EF 回血 > 1.70 或标记成 MASTERED 为准，
        // 重度词（EF 未回血）即使答对也继续保持观察
        if (repetition >= 3) {
            state.setMasteryStatus(MasteryStatus.MASTERED);
        } else if (ef.compareTo(DIFFICULT_EF_THRESHOLD) > 0) {
            state.setMasteryStatus(MasteryStatus.REVIEWING);
        } else {
            state.setMasteryStatus(MasteryStatus.DIFFICULT);
        }
        return true;
    }

    private static void schedule(UserWordState state, LocalDate today, int interval) {
        state.setIntervalDays(interval);
        state.setNextReviewDate(today.plusDays(interval));
    }
}
