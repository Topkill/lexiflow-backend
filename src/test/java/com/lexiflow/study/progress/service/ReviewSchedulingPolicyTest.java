package com.lexiflow.study.progress.service;

import com.lexiflow.study.progress.domain.MasteryStatus;
import com.lexiflow.study.progress.domain.StudyFeedback;
import com.lexiflow.study.progress.domain.UserWordState;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static com.lexiflow.study.progress.domain.AttemptType.FORMAL_REVIEW;
import static com.lexiflow.study.progress.domain.AttemptType.INITIAL_LEARNING;
import static com.lexiflow.study.progress.domain.StudyFeedback.KNOWN;
import static com.lexiflow.study.progress.domain.StudyFeedback.UNKNOWN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 复习调度核心计算的纯 Java 单元测试。
 * <p>不依赖 Spring、不连接数据库，只验证 {@link ReviewSchedulingPolicy} 的边界行为：
 * EF 上下限、间隔封顶、UNKNOWN 重置、自然脱困法摘除 DIFFICULT、同日失败门禁。</p>
 */
class ReviewSchedulingPolicyTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 21);

    private UserWordState newState() {
        UserWordState state = new UserWordState();
        state.setMasteryStatus(MasteryStatus.NEW);
        state.setLearned(false);
        state.setRepetition(0);
        state.setIntervalDays(1);
        state.setEasinessFactor(new BigDecimal("2.50"));
        state.setNextReviewDate(TODAY);
        state.setWrongCount(0);
        state.setCorrectCount(0);
        return state;
    }

    @Test
    void ef跌到下限后不再继续扣减() {
        UserWordState state = newState();
        state.setEasinessFactor(new BigDecimal("1.30"));

        boolean applied = ReviewSchedulingPolicy.apply(state, UNKNOWN, INITIAL_LEARNING, TODAY, true, false);

        assertTrue(applied);
        assertEquals(new BigDecimal("1.30"), state.getEasinessFactor(), "EF 不能低于 1.30");
    }

    @Test
    void ef涨到上限后不再继续增加() {
        UserWordState state = newState();
        state.setEasinessFactor(new BigDecimal("2.70"));
        state.setRepetition(1);

        boolean applied = ReviewSchedulingPolicy.apply(state, KNOWN, FORMAL_REVIEW, TODAY, false, true);

        assertTrue(applied);
        assertEquals(new BigDecimal("2.70"), state.getEasinessFactor(), "EF 不能超过 2.70");
    }

    @Test
    void 连续答对间隔封顶为365天() {
        UserWordState state = newState();
        state.setEasinessFactor(new BigDecimal("2.70"));
        state.setRepetition(2);
        state.setIntervalDays(300);

        boolean applied = ReviewSchedulingPolicy.apply(state, KNOWN, FORMAL_REVIEW, TODAY, false, true);

        assertTrue(applied);
        assertEquals(365, state.getIntervalDays(), "间隔不能超过 365 天");
        assertEquals(MasteryStatus.MASTERED, state.getMasteryStatus());
    }

    @Test
    void unknown失败后间隔重置为1天并清零重复次数() {
        UserWordState state = newState();
        state.setRepetition(5);
        state.setIntervalDays(90);
        state.setEasinessFactor(new BigDecimal("2.50"));

        boolean applied = ReviewSchedulingPolicy.apply(state, UNKNOWN, FORMAL_REVIEW, TODAY, true, true);

        assertTrue(applied);
        assertEquals(1, state.getIntervalDays(), "失败后间隔应为 1 天");
        assertEquals(0, state.getRepetition(), "失败后 repetition 应清零");
        assertEquals(TODAY.plusDays(1), state.getNextReviewDate());
        assertEquals(MasteryStatus.LEARNING, state.getMasteryStatus(), "首次失败 EF>1.70 应标记 LEARNING");
    }

    @Test
    void 重度困难词答对后EF未回血保持DIFICULT观察() {
        UserWordState state = newState();
        state.setEasinessFactor(new BigDecimal("1.30"));
        state.setMasteryStatus(MasteryStatus.DIFFICULT);
        state.setRepetition(0);

        boolean applied = ReviewSchedulingPolicy.apply(state, KNOWN, FORMAL_REVIEW, TODAY, false, true);

        assertTrue(applied);
        assertEquals(new BigDecimal("1.35"), state.getEasinessFactor());
        assertEquals(MasteryStatus.DIFFICULT, state.getMasteryStatus(),
                "EF 未回血到 1.70 以上，答对也应保持 DIFFICULT 观察");
    }

    @Test
    void ef回血到阈值以上后摘除困难标签() {
        UserWordState state = newState();
        state.setEasinessFactor(new BigDecimal("1.70"));
        state.setMasteryStatus(MasteryStatus.DIFFICULT);
        state.setRepetition(0);

        boolean applied = ReviewSchedulingPolicy.apply(state, KNOWN, FORMAL_REVIEW, TODAY, false, true);

        assertTrue(applied);
        assertEquals(new BigDecimal("1.75"), state.getEasinessFactor());
        assertEquals(MasteryStatus.REVIEWING, state.getMasteryStatus(),
                "EF 回血超过 1.70 应回到 REVIEWING");
    }

    @Test
    void 同日第二次失败被门禁拦截不再重复扣减() {
        UserWordState state = newState();
        boolean first = ReviewSchedulingPolicy.apply(state, UNKNOWN, INITIAL_LEARNING, TODAY, true, false);
        BigDecimal efAfterFirst = state.getEasinessFactor();
        int wrongAfterFirst = state.getWrongCount();

        boolean second = ReviewSchedulingPolicy.apply(state, UNKNOWN, FORMAL_REVIEW, TODAY, false, true);

        assertTrue(first);
        assertFalse(second, "当日第二次失败不应再推进调度");
        assertEquals(efAfterFirst, state.getEasinessFactor(), "同日重复失败不应重复扣 EF");
        assertEquals(wrongAfterFirst + 1, state.getWrongCount(), "统计字段每次失败都累计（纯统计）");
        assertEquals(1, state.getIntervalDays());
    }
}