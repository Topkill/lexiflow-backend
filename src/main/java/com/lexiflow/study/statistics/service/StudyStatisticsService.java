package com.lexiflow.study.statistics.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lexiflow.quiz.cloze.dto.ClozeAccuracyAggregate;
import com.lexiflow.quiz.cloze.mapper.ClozeAttemptMapper;
import com.lexiflow.infra.redis.RedisJsonCacheService;
import com.lexiflow.infra.redis.RedisKeys;
import com.lexiflow.study.domain.StudyPlan;
import com.lexiflow.study.domain.StudyPlanStatus;
import com.lexiflow.study.mapper.StudyPlanMapper;
import com.lexiflow.study.progress.domain.MasteryStatus;
import com.lexiflow.study.progress.domain.UserWordState;
import com.lexiflow.study.progress.domain.WrongWord;
import com.lexiflow.study.progress.mapper.StudyEventMapper;
import com.lexiflow.study.progress.mapper.UserWordStateMapper;
import com.lexiflow.study.progress.mapper.WrongWordMapper;
import com.lexiflow.study.statistics.dto.StudyStatisticsOverviewResponse;
import com.lexiflow.study.task.domain.DailyTask;
import com.lexiflow.study.task.domain.DailyTaskType;
import com.lexiflow.study.task.mapper.DailyTaskMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 学习统计服务。
 * <p>汇总用户的学习数据，包括词数统计、连续学习天数、任务完成率、测验正确率等。
 * 统计结果会缓存到 Redis（30秒 TTL）以减轻数据库压力。</p>
 */
@Service
@RequiredArgsConstructor
public class StudyStatisticsService {

    /** 统计概览缓存 TTL */
    private static final Duration OVERVIEW_CACHE_TTL = Duration.ofSeconds(30);

    private final UserWordStateMapper userWordStateMapper;
    private final WrongWordMapper wrongWordMapper;
    private final StudyEventMapper studyEventMapper;
    private final StudyPlanMapper studyPlanMapper;
    private final DailyTaskMapper dailyTaskMapper;
    private final ClozeAttemptMapper clozeAttemptMapper;
    private final RedisJsonCacheService redisJsonCacheService;

    /**
     * 获取用户的学习统计概览。
     * <p>优先从 Redis 缓存读取，缓存未命中时查询数据库并写入缓存。</p>
     *
     * @param userId 用户 ID
     * @return 学习统计概览响应
     */
    public StudyStatisticsOverviewResponse overview(Long userId) {
        String cacheKey = RedisKeys.studyStatisticsOverviewKey(userId);
        StudyStatisticsOverviewResponse cached = redisJsonCacheService.get(cacheKey, StudyStatisticsOverviewResponse.class);
        if (cached != null) {
            return cached;
        }
        StudyPlan primaryPlan = findPrimaryPlan(userId);
        Long wordbookId = primaryPlan == null ? null : primaryPlan.getWordbookId();
        Long planId = primaryPlan == null ? null : primaryPlan.getId();

        long learnedWords = countWordStates(userId, wordbookId, true, null);
        long masteredWords = countWordStates(userId, wordbookId, null, MasteryStatus.MASTERED);
        long dueReviewWords = countDueReviewWords(userId, wordbookId);
        long difficultWords = countDifficultWords(userId, wordbookId);
        int streakDays = calculateStreakDays(userId);
        BigDecimal todayTaskCompletionRate = calculateTodayTaskCompletionRate(userId, planId);
        BigDecimal clozeAccuracy = calculateClozeAccuracy(userId, wordbookId);
        BigDecimal currentWordbookProgress = calculatePlanProgress(primaryPlan);

        StudyStatisticsOverviewResponse response = new StudyStatisticsOverviewResponse(
                learnedWords,
                masteredWords,
                dueReviewWords,
                difficultWords,
                streakDays,
                todayTaskCompletionRate,
                clozeAccuracy,
                currentWordbookProgress,
                planId == null ? null : String.valueOf(planId),
                wordbookId == null ? null : String.valueOf(wordbookId)
        );
        redisJsonCacheService.set(cacheKey, response, OVERVIEW_CACHE_TTL);
        return response;
    }

    /** 查找用户的主学习计划（ACTIVE 或 PAUSED 状态）。 */
    private StudyPlan findPrimaryPlan(Long userId) {
        return studyPlanMapper.selectOne(new LambdaQueryWrapper<StudyPlan>()
                .eq(StudyPlan::getUserId, userId)
                .eq(StudyPlan::getIsPrimary, true)
                .in(StudyPlan::getStatus, StudyPlanStatus.ACTIVE, StudyPlanStatus.PAUSED)
                .orderByDesc(StudyPlan::getCreatedAt)
                .last("LIMIT 1"));
    }

    /** 按条件统计用户单词状态数量。 */
    private long countWordStates(Long userId, Long wordbookId, Boolean learned, MasteryStatus masteryStatus) {
        LambdaQueryWrapper<UserWordState> wrapper = new LambdaQueryWrapper<UserWordState>()
                .eq(UserWordState::getUserId, userId);
        if (wordbookId != null) {
            wrapper.eq(UserWordState::getWordbookId, wordbookId);
        }
        if (learned != null) {
            wrapper.eq(UserWordState::getLearned, learned);
        }
        if (masteryStatus != null) {
            wrapper.eq(UserWordState::getMasteryStatus, masteryStatus);
        }
        return userWordStateMapper.selectCount(wrapper);
    }

    /** 统计到期复习的单词数量（nextReviewDate <= 今天）。 */
    private long countDueReviewWords(Long userId, Long wordbookId) {
        LambdaQueryWrapper<UserWordState> wrapper = new LambdaQueryWrapper<UserWordState>()
                .eq(UserWordState::getUserId, userId)
                .eq(UserWordState::getLearned, true)
                .le(UserWordState::getNextReviewDate, LocalDate.now());
        if (wordbookId != null) {
            wrapper.eq(UserWordState::getWordbookId, wordbookId);
        }
        return userWordStateMapper.selectCount(wrapper);
    }

    /** 统计困难词数量（取未解决错词数和 DIFFICULT 状态数中的较大值）。 */
    private long countDifficultWords(Long userId, Long wordbookId) {
        LambdaQueryWrapper<WrongWord> wrapper = new LambdaQueryWrapper<WrongWord>()
                .eq(WrongWord::getUserId, userId)
                .eq(WrongWord::getResolved, false);
        if (wordbookId != null) {
            wrapper.eq(WrongWord::getWordbookId, wordbookId);
        }
        long wrongWords = wrongWordMapper.selectCount(wrapper);
        long difficultStates = countWordStates(userId, wordbookId, null, MasteryStatus.DIFFICULT);
        return Math.max(wrongWords, difficultStates);
    }

    /** 计算连续学习天数（从今天或昨天开始向前连续有学习记录的天数）。 */
    private int calculateStreakDays(Long userId) {
        Set<LocalDate> activeDates = new HashSet<>(studyEventMapper.selectActiveDates(userId));
        LocalDate cursor = LocalDate.now();
        if (!activeDates.contains(cursor)) {
            cursor = cursor.minusDays(1);
        }
        int streak = 0;
        while (activeDates.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }

    /** 计算今日任务完成率（百分比）。 */
    private BigDecimal calculateTodayTaskCompletionRate(Long userId, Long planId) {
        LambdaQueryWrapper<DailyTask> wrapper = new LambdaQueryWrapper<DailyTask>()
                .eq(DailyTask::getUserId, userId)
                .eq(DailyTask::getTaskType, DailyTaskType.DAILY)
                .eq(DailyTask::getTaskDate, LocalDate.now());
        if (planId != null) {
            wrapper.eq(DailyTask::getPlanId, planId);
        }
        DailyTask task = dailyTaskMapper.selectOne(wrapper.orderByDesc(DailyTask::getCreatedAt).last("LIMIT 1"));
        if (task == null) {
            return BigDecimal.ZERO.setScale(2);
        }
        int total = safe(task.getNewCount()) + safe(task.getReviewCount()) + safe(task.getExtraCount());
        if (total <= 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return percent(BigDecimal.valueOf(safe(task.getDoneCount())), BigDecimal.valueOf(total));
    }

    /** 计算完形填空正确率（百分比）。 */
    private BigDecimal calculateClozeAccuracy(Long userId, Long wordbookId) {
        ClozeAccuracyAggregate aggregate = clozeAttemptMapper.sumAccuracy(userId, wordbookId);
        long total = safe(aggregate == null ? null : aggregate.getTotalBlanks());
        long correct = safe(aggregate == null ? null : aggregate.getCorrectCount());
        if (total <= 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return percent(BigDecimal.valueOf(correct), BigDecimal.valueOf(total));
    }

    /** 计算学习计划完成进度（已学词数 / 总词数）。 */
    private BigDecimal calculatePlanProgress(StudyPlan plan) {
        if (plan == null || plan.getTotalWords() == null || plan.getTotalWords() <= 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return percent(BigDecimal.valueOf(safe(plan.getLearnedCount())), BigDecimal.valueOf(plan.getTotalWords()));
    }

    /** 计算百分比，保留 2 位小数。 */
    private BigDecimal percent(BigDecimal numerator, BigDecimal denominator) {
        return numerator.multiply(new BigDecimal("100")).divide(denominator, 2, RoundingMode.HALF_UP);
    }

    /** 安全地将可能为 null 的 Integer 转换为 int。 */
    private int safe(Integer value) {
        return value == null ? 0 : value;
    }

    /** 安全地将可能为 null 的 Long 转换为 long。 */
    private long safe(Long value) {
        return value == null ? 0L : value;
    }
}
