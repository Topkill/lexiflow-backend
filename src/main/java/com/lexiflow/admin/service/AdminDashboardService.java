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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    private static final Duration OVERVIEW_CACHE_TTL = Duration.ofSeconds(15);

    private final UserMapper userMapper;
    private final WordbookMapper wordbookMapper;
    private final WordMapper wordMapper;
    private final StudyEventMapper studyEventMapper;
    private final AiCallLogMapper aiCallLogMapper;
    private final RedisJsonCacheService redisJsonCacheService;

    public AdminOverviewResponse overview() {
        String cacheKey = RedisKeys.adminOverviewKey();
        AdminOverviewResponse cached = redisJsonCacheService.get(cacheKey, AdminOverviewResponse.class);
        if (cached != null) {
            return cached;
        }
        LocalDate today = LocalDate.now();
        long registeredUsers = userMapper.selectCount(new LambdaQueryWrapper<User>());
        long activeUsers = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getStatus, UserStatus.ACTIVE));
        long todayLearners = studyEventMapper.countDistinctUsersBetween(today.atStartOfDay(), today.plusDays(1).atStartOfDay());
        long wordbookCount = wordbookMapper.selectCount(new LambdaQueryWrapper<Wordbook>());
        long wordCount = wordMapper.selectCount(new LambdaQueryWrapper<Word>());
        long aiCallCount = aiCallLogMapper.selectCount(new LambdaQueryWrapper<AiCallLog>());
        long aiSuccessCount = aiCallLogMapper.selectCount(new LambdaQueryWrapper<AiCallLog>()
                .eq(AiCallLog::getStatus, AiCallStatus.SUCCESS));
        BigDecimal aiSuccessRate = aiCallCount == 0 ? BigDecimal.ZERO.setScale(2) : BigDecimal.valueOf(aiSuccessCount)
                .multiply(new BigDecimal("100"))
                .divide(BigDecimal.valueOf(aiCallCount), 2, RoundingMode.HALF_UP);
        long todayAiCallCount = aiCallLogMapper.selectCount(new LambdaQueryWrapper<AiCallLog>()
                .ge(AiCallLog::getCreatedAt, today.atStartOfDay())
                .lt(AiCallLog::getCreatedAt, today.plusDays(1).atStartOfDay()));
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
