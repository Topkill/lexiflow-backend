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

/**
 * AI 配额管理服务
 * <p>
 * 管理用户公共 AI 调用的每日配额。
 * 采用 Redis 计数器 + 数据库日志双重校验机制，
 * 确保配额检查的原子性和准确性。配额基于亚洲/上海时区按天重置。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class AiQuotaService {

    private static final ZoneId QUOTA_ZONE = ZoneId.of("Asia/Shanghai");

    private final AiCallLogMapper aiCallLogMapper;
    private final RedisAiQuotaCounter redisAiQuotaCounter;

    /**
     * 检查用户公共 AI 调用配额
     * <p>
     * 仅对公共配置生效，通过 Redis 原子操作预留配额，
     * 若 Redis 计数器未初始化则回源到数据库日志计算已用次数。
     * </p>
     *
     * @param userId 用户 ID
     * @param config AI 运行时配置
     * @throws BizException 当配额耗尽时
     */
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
