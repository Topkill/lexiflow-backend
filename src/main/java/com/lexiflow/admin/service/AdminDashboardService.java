package com.lexiflow.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lexiflow.admin.dto.AdminOverviewResponse;
import com.lexiflow.ai.content.domain.AiCallLog;
import com.lexiflow.ai.content.domain.AiCallStatus;
import com.lexiflow.ai.content.mapper.AiCallLogMapper;
import com.lexiflow.infra.redis.RedisJsonCacheService;
import com.lexiflow.infra.redis.RedisKeys;
import com.lexiflow.study.progress.mapper.StudyEventMapper;
import com.lexiflow.user.domain.User;
import com.lexiflow.user.domain.UserStatus;
import com.lexiflow.user.mapper.UserMapper;
import com.lexiflow.wordbook.domain.Word;
import com.lexiflow.wordbook.domain.Wordbook;
import com.lexiflow.wordbook.mapper.WordMapper;
import com.lexiflow.wordbook.mapper.WordbookMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;

/**
 * 后台看板服务。
 *
 * <p>提供管理员数据看板概览功能，统计用户、词库、单词、AI 调用等核心业务指标。
 * 结果缓存 15 秒以减少数据库压力。</p>
 */
@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    /** 概览数据缓存 TTL：15 秒 */
    private static final Duration OVERVIEW_CACHE_TTL = Duration.ofSeconds(15);

    private final UserMapper userMapper;
    private final WordbookMapper wordbookMapper;
    private final WordMapper wordMapper;
    private final StudyEventMapper studyEventMapper;
    private final AiCallLogMapper aiCallLogMapper;
    private final RedisJsonCacheService redisJsonCacheService;
    /**
     * 获取管理员概览数据。
     * <p>
     * 该方法首先尝试从 Redis 缓存中获取概览数据，如果缓存命中则直接返回。
     * 若缓存未命中，则从数据库统计各项业务指标，包括注册用户数、活跃用户数、今日学习人数、
     * 词库数量、单词数量、AI 调用总数、AI 成功率及今日 AI 调用次数，并将结果写入缓存后返回。
     *
     * @return AdminOverviewResponse 包含各项统计指标的管理员概览响应对象
     */
    public AdminOverviewResponse overview() {
        String cacheKey = RedisKeys.adminOverviewKey();
        AdminOverviewResponse cached = redisJsonCacheService.get(cacheKey, AdminOverviewResponse.class);
        if (cached != null) {
            return cached;
        }

        // 统计基础用户数据
        LocalDate today = LocalDate.now();
        long registeredUsers = userMapper.selectCount(new LambdaQueryWrapper<User>());
        long activeUsers = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getStatus, UserStatus.ACTIVE));
        long todayLearners = studyEventMapper.countDistinctUsersBetween(today.atStartOfDay(), today.plusDays(1).atStartOfDay());

        // 统计词库与单词数据
        long wordbookCount = wordbookMapper.selectCount(new LambdaQueryWrapper<Wordbook>());
        long wordCount = wordMapper.selectCount(new LambdaQueryWrapper<Word>());

        // 统计 AI 调用相关数据及成功率
        long aiCallCount = aiCallLogMapper.selectCount(new LambdaQueryWrapper<AiCallLog>());
        long aiSuccessCount = aiCallLogMapper.selectCount(new LambdaQueryWrapper<AiCallLog>()
                .eq(AiCallLog::getStatus, AiCallStatus.SUCCESS));
        BigDecimal aiSuccessRate = aiCallCount == 0 ? BigDecimal.ZERO.setScale(2) : BigDecimal.valueOf(aiSuccessCount)
                .multiply(new BigDecimal("100"))
                .divide(BigDecimal.valueOf(aiCallCount), 2, RoundingMode.HALF_UP);
        long todayAiCallCount = aiCallLogMapper.selectCount(new LambdaQueryWrapper<AiCallLog>()
                .ge(AiCallLog::getCreatedAt, today.atStartOfDay())
                .lt(AiCallLog::getCreatedAt, today.plusDays(1).atStartOfDay()));

        // 构建响应对象并更新缓存
        AdminOverviewResponse response = new AdminOverviewResponse(
                registeredUsers,
                activeUsers,
                todayLearners,
                wordbookCount,
                wordCount,
                aiCallCount,
                aiSuccessRate,
                todayAiCallCount
        );
        redisJsonCacheService.set(cacheKey, response, OVERVIEW_CACHE_TTL);
        return response;
    }

}
