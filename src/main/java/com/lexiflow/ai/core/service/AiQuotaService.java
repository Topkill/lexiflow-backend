package com.lexiflow.ai.core.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lexiflow.ai.content.domain.AiCallLog;
import com.lexiflow.ai.content.mapper.AiCallLogMapper;
import com.lexiflow.ai.core.dto.AiConfigScope;
import com.lexiflow.ai.core.dto.AiRuntimeConfig;
import com.lexiflow.common.error.ErrorCode;
import com.lexiflow.common.exception.BizException;
import com.lexiflow.infra.redis.RedisAiQuotaCounter;
import com.lexiflow.infra.redis.RedisQuotaReserveResult;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AiQuotaService {

    private static final ZoneId QUOTA_ZONE = ZoneId.of("Asia/Shanghai");

    private final AiCallLogMapper aiCallLogMapper;
    private final RedisAiQuotaCounter redisAiQuotaCounter;

    public void checkQuota(Long userId, AiRuntimeConfig config) {
        if (config.scope() != AiConfigScope.PUBLIC) {
            return;
        }
        int quota = config.dailyQuotaPerUser() == null ? 0 : config.dailyQuotaPerUser();
        if (quota <= 0) {
            throw new BizException(ErrorCode.AI_PUBLIC_QUOTA_EXHAUSTED);
        }
        LocalDate today = LocalDate.now(QUOTA_ZONE);
        RedisQuotaReserveResult reserveResult = redisAiQuotaCounter.reserveDailyQuota(userId, quota, today, ttlUntilNextDay());
        if (reserveResult.allowed()) {
            return;
        }
        if (reserveResult.exhausted()) {
            throw new BizException(ErrorCode.AI_PUBLIC_QUOTA_EXHAUSTED);
        }
        if (reserveResult.initializationRequired()) {
            reserveQuotaWithLogBaseline(userId, quota, today);
            return;
        }
        checkQuotaFromLog(userId, quota, today);
    }

    private void checkQuotaFromLog(Long userId, int quota, LocalDate today) {
        long used = countDailyPublicCalls(userId, today);
        if (used >= quota) {
            throw new BizException(ErrorCode.AI_PUBLIC_QUOTA_EXHAUSTED);
        }
    }

    private void reserveQuotaWithLogBaseline(Long userId, int quota, LocalDate today) {
        long usedFromLog = countDailyPublicCalls(userId, today);
        RedisQuotaReserveResult reserveResult = redisAiQuotaCounter.initializeAndReserveDailyQuota(
                userId,
                quota,
                today,
                ttlUntilNextDay(),
                usedFromLog
        );
        if (reserveResult.allowed()) {
            return;
        }
        if (reserveResult.exhausted() || usedFromLog >= quota) {
            throw new BizException(ErrorCode.AI_PUBLIC_QUOTA_EXHAUSTED);
        }
    }

    private long countDailyPublicCalls(Long userId, LocalDate today) {
        Long used = aiCallLogMapper.selectCount(new LambdaQueryWrapper<AiCallLog>()
                .eq(AiCallLog::getUserId, userId)
                .eq(AiCallLog::getConfigScope, AiConfigScope.PUBLIC)
                .ge(AiCallLog::getCreatedAt, today.atStartOfDay())
                .lt(AiCallLog::getCreatedAt, today.plusDays(1).atStartOfDay()));
        return used == null ? 0 : used;
    }

    private Duration ttlUntilNextDay() {
        ZonedDateTime now = ZonedDateTime.now(QUOTA_ZONE);
        ZonedDateTime nextDay = now.toLocalDate().plusDays(1).atStartOfDay(QUOTA_ZONE);
        return Duration.between(now, nextDay);
    }
}
