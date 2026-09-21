package com.lexiflow.study.progress.service;

import com.lexiflow.study.progress.domain.MasteryStatus;
import com.lexiflow.study.progress.domain.StudyFeedback;
import com.lexiflow.study.progress.domain.UserWordState;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

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

    // ---- Anki Good 档逾期折半公式：(原间隔 + 逾期天数/2) × EF ----

    @Test
    void 准时复习沿用间隔乘EF() {
        UserWordState state = newState();
        state.setEasinessFactor(new BigDecimal("2.50"));
        state.setRepetition(2);
        state.setIntervalDays(10);
        state.setNextReviewDate(TODAY); // 今天到期、今天复习 → 逾期 0 天

        ReviewSchedulingPolicy.apply(state, KNOWN, FORMAL_REVIEW, TODAY, false, true);

        assertEquals(26, state.getIntervalDays(), "准时复习应退化为 间隔×(EF+0.05)：10×2.55=25.5 → HALF_UP=26");
    }

    @Test
    void 逾期六天答对间隔按折半公式平滑增长() {
        UserWordState state = newState();
        state.setEasinessFactor(new BigDecimal("2.50"));
        state.setRepetition(2);
        state.setIntervalDays(10);
        state.setNextReviewDate(TODAY.minusDays(6)); // 逾期 6 天

        ReviewSchedulingPolicy.apply(state, KNOWN, FORMAL_REVIEW, TODAY, false, true);

        assertEquals(33, state.getIntervalDays(), "(10+6÷2)×2.5=32.5 → HALF_UP=33");
    }

    @Test
    void 逾期三十天答对间隔大幅奖励但封顶() {
        UserWordState state = newState();
        state.setEasinessFactor(new BigDecimal("2.50"));
        state.setRepetition(2);
        state.setIntervalDays(10);
        state.setNextReviewDate(TODAY.minusDays(30)); // 逾期 30 天

        ReviewSchedulingPolicy.apply(state, KNOWN, FORMAL_REVIEW, TODAY, false, true);

        assertEquals(64, state.getIntervalDays(), "(10+30÷2)×(2.50+0.05)=63.75 → HALF_UP=64");
    }

    @Test
    void 断卡很久答对间隔封顶365天() {
        UserWordState state = newState();
        state.setEasinessFactor(new BigDecimal("2.50"));
        state.setRepetition(2);
        state.setIntervalDays(100);
        state.setNextReviewDate(TODAY.minusDays(300)); // 断卡 300 天

        ReviewSchedulingPolicy.apply(state, KNOWN, FORMAL_REVIEW, TODAY, false, true);

        assertEquals(365, state.getIntervalDays(), "(100+300÷2)×2.5 超出上限应封顶 365");
    }

    @Test
    void 间隔不低于前次间隔加一天防倒退() {
        UserWordState state = newState();
        state.setEasinessFactor(new BigDecimal("1.30"));
        state.setRepetition(2);
        state.setIntervalDays(200);
        state.setNextReviewDate(TODAY);

        ReviewSchedulingPolicy.apply(state, KNOWN, FORMAL_REVIEW, TODAY, false, true);

        assertTrue(state.getIntervalDays() >= 201, "新间隔不得低于 prev+1（防倒退）");
    }

    @Test
    void 前两次复习间隔保持1天和3天阶梯() {
        UserWordState state = newState();
        ReviewSchedulingPolicy.apply(state, KNOWN, INITIAL_LEARNING, TODAY, false, false);
        assertEquals(1, state.getIntervalDays(), "第一次复习间隔应为 1 天");
        assertEquals(1, state.getRepetition());

        ReviewSchedulingPolicy.apply(state, KNOWN, FORMAL_REVIEW, TODAY, false, true);
        assertEquals(3, state.getIntervalDays(), "第二次复习间隔应为 3 天（阶梯不被公式接管）");
        assertEquals(2, state.getRepetition());
    }

    // ---- 业务日偏移（凌晨 4 点算新一天） ----

    @Test
    void 深夜学习与次日凌晨归属同一业务日() {
        assertEquals(LocalDate.of(2026, 9, 21), StudyBusinessTime.businessDateOf(LocalDateTime.of(2026, 9, 21, 23, 59)),
                "23:59 仍属当天业务日");
        assertEquals(LocalDate.of(2026, 9, 21), StudyBusinessTime.businessDateOf(LocalDateTime.of(2026, 9, 22, 0, 1)),
                "00:01 仍属前一天业务日，避免跨午夜跳变");
        assertEquals(LocalDate.of(2026, 9, 21), StudyBusinessTime.businessDateOf(LocalDateTime.of(2026, 9, 21, 4, 0)),
                "凌晨 4 点整进入新业务日");
        assertEquals(LocalDate.of(2026, 9, 20), StudyBusinessTime.businessDateOf(LocalDateTime.of(2026, 9, 21, 3, 59)),
                "凌晨 4 点前仍属前一天业务日");
    }
}