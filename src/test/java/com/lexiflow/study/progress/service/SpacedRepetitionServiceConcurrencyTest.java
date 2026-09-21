package com.lexiflow.study.progress.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lexiflow.study.progress.domain.AttemptType;
import com.lexiflow.study.progress.domain.StudyDailyWordEffect;
import com.lexiflow.study.progress.domain.StudyFeedback;
import com.lexiflow.study.progress.domain.StudyScene;
import com.lexiflow.study.progress.domain.UserWordState;
import com.lexiflow.study.progress.mapper.StudyDailyWordEffectMapper;
import com.lexiflow.study.progress.mapper.UserWordStateMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
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
 * 每日效果只有一条记录），其余反馈被门禁拦截，且不抛死锁/重复键异常。</p>
 */
@SpringBootTest
class SpacedRepetitionServiceConcurrencyTest {

    private static final long TEST_USER_ID = 999999001L;
    private static final long TEST_WORDBOOK_ID = 999999001L;
    private static final long TEST_WORD_ID = 999999001L;
    private static final LocalDate TODAY = LocalDate.now();

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.username", () -> "debian-sys-maint");
        registry.add("spring.datasource.password", () -> "11cq0ocSXYmkfHc4");
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

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    go.await();
                    spacedRepetitionService.applyFeedback(
                            TEST_USER_ID, TEST_WORDBOOK_ID, TEST_WORD_ID, null,
                            StudyFeedback.UNKNOWN, StudyScene.NEW, AttemptType.INITIAL_LEARNING);
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

        // 单词状态只有一条，EF 只扣一次：2.50 -> 2.30
        List<UserWordState> states = userWordStateMapper.selectList(new LambdaQueryWrapper<UserWordState>()
                .eq(UserWordState::getUserId, TEST_USER_ID)
                .eq(UserWordState::getWordId, TEST_WORD_ID));
        assertEquals(1, states.size(), "单词状态应只有一条记录");
        assertEquals(0, states.get(0).getEasinessFactor()
                        .compareTo(new BigDecimal("2.30")),
                "EF 应只扣一次（2.50 -> 2.30）");

        // 每日效果记录只有一条，且只应用了一次 UNKNOWN 惩罚
        Long effectCount = dailyEffectMapper.selectCount(new LambdaQueryWrapper<StudyDailyWordEffect>()
                .eq(StudyDailyWordEffect::getUserId, TEST_USER_ID)
                .eq(StudyDailyWordEffect::getWordId, TEST_WORD_ID)
                .eq(StudyDailyWordEffect::getBusinessDate, TODAY));
        assertEquals(1L, effectCount, "每日效果记录应只有一条");
        StudyDailyWordEffect effect = dailyEffectMapper.selectOne(new LambdaQueryWrapper<StudyDailyWordEffect>()
                .eq(StudyDailyWordEffect::getUserId, TEST_USER_ID)
                .eq(StudyDailyWordEffect::getWordId, TEST_WORD_ID)
                .eq(StudyDailyWordEffect::getBusinessDate, TODAY));
        assertEquals(Boolean.TRUE, effect.getUnknownEfApplied(), "UNKNOWN 惩罚应只应用一次");
    }
}