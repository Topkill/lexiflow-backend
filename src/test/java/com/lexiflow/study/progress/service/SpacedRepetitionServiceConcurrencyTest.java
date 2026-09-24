package com.lexiflow.study.progress.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lexiflow.study.progress.domain.AttemptType;
import com.lexiflow.study.progress.domain.MasteryStatus;
import com.lexiflow.study.progress.domain.StudyDailyWordEffect;
import com.lexiflow.study.progress.domain.StudyFeedback;
import com.lexiflow.study.progress.domain.StudyScene;
import com.lexiflow.study.progress.domain.UserWordState;
import com.lexiflow.study.progress.mapper.StudyDailyWordEffectMapper;
import com.lexiflow.study.progress.mapper.UserWordStateMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 复习调度反馈并发集成测试（真实 MySQL）。
 * <p>多个线程同时对同一个用户、同一个词提交 UNKNOWN 反馈，
 * 验证用户行锁串行化生效：仅首次反馈推进调度（EF 只扣一次、
 * 每日效果只有一条记录），其余反馈被门禁拦截，且不抛死锁/重复键异常。
 * 每次真实失败仍各自留痕，因此 {@code wrongCount} 应累加到并发线程数。</p>
 *
 * <p><b>运行方式</b>：本测试需要真实 MySQL（含已执行的迁移），默认跳过以免污染开发机或 CI。
 * 显式启用：</p>
 * <pre>
 * LEXIFLOW_TEST_DB=true LEXIFLOW_DB_USER=&lt;user&gt; LEXIFLOW_DB_PASSWORD=&lt;pass&gt; mvn test
 * </pre>
 * 凭据一律从环境变量读取，不写入仓库。未设置 {@code LEXIFLOW_TEST_DB=true} 时整类跳过。
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "LEXIFLOW_TEST_DB", matches = "true")
class SpacedRepetitionServiceConcurrencyTest {

    private static final long TEST_USER_ID = 999999001L;
    private static final long TEST_WORDBOOK_ID = 999999001L;
    private static final long TEST_WORD_ID = 999999001L;
    /** 与生产一致的业务日口径（Asia/Shanghai + 凌晨 4 点偏移），不使用 JVM 默认时区的自然日。 */
    private static final LocalDate BUSINESS_DAY = currentBusinessDay();

    private static LocalDate currentBusinessDay() {
        return StudyBusinessTime.businessDateOf(LocalDateTime.now(StudyBusinessTime.ZONE));
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.username", () -> System.getenv("LEXIFLOW_DB_USER"));
        registry.add("spring.datasource.password", () -> System.getenv("LEXIFLOW_DB_PASSWORD"));
    }

    @Autowired
    private SpacedRepetitionService spacedRepetitionService;
    @Autowired
    private UserWordStateMapper userWordStateMapper;
    @Autowired
    private StudyDailyWordEffectMapper dailyEffectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("""
                INSERT INTO users (id, email, password_hash, nickname, role, status, token_version, deleted)
                VALUES (?, 'concurrency_test@test.local', 'x', '并发测试', 'USER', 'ACTIVE', 1, 0)
                """, TEST_USER_ID);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM study_daily_word_effect WHERE user_id = ?", TEST_USER_ID);
        jdbcTemplate.update("DELETE FROM user_word_state WHERE user_id = ?", TEST_USER_ID);
        jdbcTemplate.update("DELETE FROM users WHERE id = ?", TEST_USER_ID);
    }

    @Test
    void 同用户同词并发失败反馈仅首次推进调度() throws Exception {
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());
        List<SpacedRepetitionService.SpacedRepetitionResult> results = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    go.await();
                    SpacedRepetitionService.SpacedRepetitionResult r = spacedRepetitionService.applyFeedback(
                            TEST_USER_ID, TEST_WORDBOOK_ID, TEST_WORD_ID, null,
                            StudyFeedback.UNKNOWN, StudyScene.NEW, AttemptType.INITIAL_LEARNING);
                    results.add(r);
                } catch (Throwable t) {
                    errors.add(t);
                } finally {
                    done.countDown();
                }
            });
        }

        assertTrue(ready.await(10, TimeUnit.SECONDS), "线程未就绪");
        go.countDown();
        assertTrue(done.await(60, TimeUnit.SECONDS), "并发反馈超时未完成");
        pool.shutdown();

        assertTrue(errors.isEmpty(), "并发提交不应抛出异常: " + errors);
        assertEquals(threads, results.size(), "每个线程都应拿到一次调度结果");

        // 单词状态只有一条，EF 只扣一次：2.50 -> 2.30
        List<UserWordState> states = userWordStateMapper.selectList(new LambdaQueryWrapper<UserWordState>()
                .eq(UserWordState::getUserId, TEST_USER_ID)
                .eq(UserWordState::getWordId, TEST_WORD_ID));
        assertEquals(1, states.size(), "单词状态应只有一条记录");
        assertEquals(0, states.get(0).getEasinessFactor()
                        .compareTo(new BigDecimal("2.30")),
                "EF 应只扣一次（2.50 -> 2.30）");
        // 每日额度只限制调度效果，不限制统计留痕：每次真实失败都计一次
        assertEquals(threads, states.get(0).getWrongCount(),
                "wrongCount 为失败尝试次数，每次真实失败都应累加");

        // 每日效果记录只有一条，且只应用了一次 UNKNOWN 惩罚。
        // 这里刻意不按 business_date 过滤：测试若跨越凌晨 4 点业务日切换，
        // 固定日期条件会造成与被测代码无关的偶发失败。业务日取值本身的边界
        // 由 ReviewSchedulingPolicyTest 的业务日用例覆盖。
        Long effectCount = dailyEffectMapper.selectCount(new LambdaQueryWrapper<StudyDailyWordEffect>()
                .eq(StudyDailyWordEffect::getUserId, TEST_USER_ID)
                .eq(StudyDailyWordEffect::getWordId, TEST_WORD_ID));
        assertEquals(1L, effectCount, "每日效果记录应只有一条");
        StudyDailyWordEffect effect = dailyEffectMapper.selectOne(new LambdaQueryWrapper<StudyDailyWordEffect>()
                .eq(StudyDailyWordEffect::getUserId, TEST_USER_ID)
                .eq(StudyDailyWordEffect::getWordId, TEST_WORD_ID));
        assertEquals(Boolean.TRUE, effect.getUnknownEfApplied(), "UNKNOWN 惩罚应只应用一次");
        assertEquals(Boolean.FALSE, effect.getKnownReviewApplied(), "本次没有 KNOWN 反馈，不应置成功额度");
        // 记录必须落在业务日上；容忍测试期间恰好跨越凌晨 4 点的日切
        LocalDate storedDay = effect.getBusinessDate();
        assertTrue(storedDay.equals(BUSINESS_DAY) || storedDay.equals(currentBusinessDay()),
                "每日效果记录应归属于业务日，实际=" + storedDay);

        // 算法快照：从并发结果中筛选出真正生效的那次调度进行断言
        // （不能用 results.get(0)：add 在事务提交后才执行，后续线程可能抢先入列）
        long appliedCount = results.stream()
                .filter(SpacedRepetitionService.SpacedRepetitionResult::algorithmApplied)
                .count();
        assertEquals(1L, appliedCount, "并发提交中只应有一次真正推进调度");
        SpacedRepetitionService.SpacedRepetitionResult appliedResult = results.stream()
                .filter(SpacedRepetitionService.SpacedRepetitionResult::algorithmApplied)
                .findFirst()
                .orElseThrow(() -> new AssertionError("未找到生效的算法调度结果"));
        assertEquals(new BigDecimal("2.50"), appliedResult.efBefore(), "快照变更前 EF 应为初始值");
        assertEquals(new BigDecimal("2.30"), appliedResult.efAfter(), "快照变更后 EF 应只扣一次");
        assertEquals(1, appliedResult.intervalDaysAfter(), "快照变更后间隔应为 1 天");
        assertEquals(0, appliedResult.repetitionBefore(), "快照变更前 repetition 应为 0");
        assertEquals(0, appliedResult.repetitionAfter(), "UNKNOWN 后 repetition 应为 0");
        assertEquals(MasteryStatus.NEW, appliedResult.oldMasteryStatus(), "快照变更前掌握状态应为 NEW");
        assertEquals(MasteryStatus.LEARNING, appliedResult.newMasteryStatus(), "首次失败 EF>1.70 应为 LEARNING");
        assertEquals("V2_BOUNDED_STEP", appliedResult.algorithmVersion(), "快照应带算法版本号");
    }
}