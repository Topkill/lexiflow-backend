package com.lexiflow.ai.core.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lexiflow.ai.content.domain.AiCallLog;
import com.lexiflow.ai.content.mapper.AiCallLogMapper;
import com.lexiflow.ai.core.dto.AiConfigScope;
import com.lexiflow.ai.core.dto.AiRuntimeConfig;
import com.lexiflow.common.error.ErrorCode;
import com.lexiflow.common.exception.BizException;
import com.lexiflow.infra.redis.RedisAiQuotaCounter;
import com.lexiflow.infra.redis.RedisQuotaReserveResult;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AiQuotaServiceTest {

    @Mock
    private AiCallLogMapper aiCallLogMapper;
    @Mock
    private RedisAiQuotaCounter redisAiQuotaCounter;

    @Test
    void privateConfigShouldSkipQuota() {
        AiQuotaService service = new AiQuotaService(aiCallLogMapper, redisAiQuotaCounter);

        service.checkQuota(9L, runtimeConfig(AiConfigScope.PRIVATE, null));

        verify(redisAiQuotaCounter, never()).reserveDailyQuota(any(), anyInt(), any(LocalDate.class), any(Duration.class));
        verify(aiCallLogMapper, never()).selectCount(any());
    }

    @Test
    void publicQuotaShouldPassWhenRedisAllows() {
        AiQuotaService service = new AiQuotaService(aiCallLogMapper, redisAiQuotaCounter);
        when(redisAiQuotaCounter.reserveDailyQuota(eq(9L), eq(10), any(LocalDate.class), any(Duration.class)))
                .thenReturn(RedisQuotaReserveResult.allowed(3));

        service.checkQuota(9L, runtimeConfig(AiConfigScope.PUBLIC, 10));

        verify(aiCallLogMapper, never()).selectCount(any());
    }

    @Test
    void publicQuotaShouldFailWhenRedisExhausted() {
        AiQuotaService service = new AiQuotaService(aiCallLogMapper, redisAiQuotaCounter);
        when(redisAiQuotaCounter.reserveDailyQuota(eq(9L), eq(10), any(LocalDate.class), any(Duration.class)))
                .thenReturn(RedisQuotaReserveResult.exhaustedResult());

        assertThatThrownBy(() -> service.checkQuota(9L, runtimeConfig(AiConfigScope.PUBLIC, 10)))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.AI_PUBLIC_QUOTA_EXHAUSTED);
    }

    @Test
    void publicQuotaShouldFallbackToLogCountWhenRedisUnavailable() {
        AiQuotaService service = new AiQuotaService(aiCallLogMapper, redisAiQuotaCounter);
        when(redisAiQuotaCounter.reserveDailyQuota(eq(9L), eq(10), any(LocalDate.class), any(Duration.class)))
                .thenReturn(RedisQuotaReserveResult.unavailableResult());
        when(aiCallLogMapper.selectCount(any())).thenReturn(4L);

        service.checkQuota(9L, runtimeConfig(AiConfigScope.PUBLIC, 10));

        verify(aiCallLogMapper).selectCount(any());
    }

    @Test
    void publicQuotaShouldInitializeRedisFromLogWhenCounterMissing() {
        AiQuotaService service = new AiQuotaService(aiCallLogMapper, redisAiQuotaCounter);
        when(redisAiQuotaCounter.reserveDailyQuota(eq(9L), eq(10), any(LocalDate.class), any(Duration.class)))
                .thenReturn(RedisQuotaReserveResult.initializationRequiredResult());
        when(aiCallLogMapper.selectCount(any())).thenReturn(4L);
        when(redisAiQuotaCounter.initializeAndReserveDailyQuota(eq(9L), eq(10), any(LocalDate.class), any(Duration.class), eq(4L)))
                .thenReturn(RedisQuotaReserveResult.allowed(5));

        service.checkQuota(9L, runtimeConfig(AiConfigScope.PUBLIC, 10));

        verify(redisAiQuotaCounter).initializeAndReserveDailyQuota(eq(9L), eq(10), any(LocalDate.class), any(Duration.class), eq(4L));
    }

    @Test
    void publicQuotaShouldFailWhenCounterMissingAndLogCountReachedQuota() {
        AiQuotaService service = new AiQuotaService(aiCallLogMapper, redisAiQuotaCounter);
        when(redisAiQuotaCounter.reserveDailyQuota(eq(9L), eq(10), any(LocalDate.class), any(Duration.class)))
                .thenReturn(RedisQuotaReserveResult.initializationRequiredResult());
        when(aiCallLogMapper.selectCount(any())).thenReturn(10L);
        when(redisAiQuotaCounter.initializeAndReserveDailyQuota(eq(9L), eq(10), any(LocalDate.class), any(Duration.class), eq(10L)))
                .thenReturn(RedisQuotaReserveResult.exhaustedResult());

        assertThatThrownBy(() -> service.checkQuota(9L, runtimeConfig(AiConfigScope.PUBLIC, 10)))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.AI_PUBLIC_QUOTA_EXHAUSTED);
    }

    @Test
    void publicQuotaShouldFailWhenFallbackLogCountReachedQuota() {
        AiQuotaService service = new AiQuotaService(aiCallLogMapper, redisAiQuotaCounter);
        when(redisAiQuotaCounter.reserveDailyQuota(eq(9L), eq(10), any(LocalDate.class), any(Duration.class)))
                .thenReturn(RedisQuotaReserveResult.unavailableResult());
        when(aiCallLogMapper.selectCount(any())).thenReturn(10L);

        assertThatThrownBy(() -> service.checkQuota(9L, runtimeConfig(AiConfigScope.PUBLIC, 10)))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.AI_PUBLIC_QUOTA_EXHAUSTED);
    }

    private AiRuntimeConfig runtimeConfig(AiConfigScope scope, Integer quota) {
        return new AiRuntimeConfig(
                scope,
                "http://localhost:8115/v1",
                "test-key",
                "test-model",
                new BigDecimal("0.2"),
                true,
                quota
        );
    }
}
