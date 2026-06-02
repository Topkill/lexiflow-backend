package com.lexiflow.auth.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lexiflow.common.error.ErrorCode;
import com.lexiflow.common.exception.BizException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

@ExtendWith(MockitoExtension.class)
class AuthRateLimitServiceTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    @Test
    void assertLoginAllowedShouldPassWhenCountersBelowLimit() {
        AuthRateLimitService service = new AuthRateLimitService(stringRedisTemplate);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn("4");

        assertThatCode(() -> service.assertLoginAllowed("USER@example.com", "127.0.0.1"))
                .doesNotThrowAnyException();

        verify(valueOperations, times(2)).get(anyString());
    }

    @Test
    void assertLoginAllowedShouldBlockWhenCounterReachesLimit() {
        AuthRateLimitService service = new AuthRateLimitService(stringRedisTemplate);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn("5");

        assertThatThrownBy(() -> service.assertLoginAllowed("user@example.com", "127.0.0.1"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.TOO_MANY_REQUESTS);
    }

    @Test
    void recordLoginFailureShouldIncrementEmailAndIpCountersWithWindow() {
        AuthRateLimitService service = new AuthRateLimitService(stringRedisTemplate);
        when(stringRedisTemplate.execute(any(DefaultRedisScript.class), any(List.class), eq("600000")))
                .thenReturn(1L);

        service.recordLoginFailure("USER@example.com", "127.0.0.1");

        verify(stringRedisTemplate, times(2))
                .execute(any(DefaultRedisScript.class), any(List.class), eq("600000"));
    }

    @Test
    void clearLoginFailuresShouldDeleteEmailCounterOnly() {
        AuthRateLimitService service = new AuthRateLimitService(stringRedisTemplate);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);

        service.clearLoginFailures("USER@example.com");

        verify(stringRedisTemplate).delete(keyCaptor.capture());
        assertThat(keyCaptor.getValue()).startsWith("lexiflow:auth:login:email:");
    }

    @Test
    void redisUnavailableShouldFailOpen() {
        AuthRateLimitService service = new AuthRateLimitService(stringRedisTemplate);
        when(stringRedisTemplate.opsForValue()).thenThrow(new RuntimeException("redis down"));
        when(stringRedisTemplate.execute(any(DefaultRedisScript.class), any(List.class), anyString()))
                .thenThrow(new RuntimeException("redis down"));

        assertThatCode(() -> service.assertLoginAllowed("user@example.com", "127.0.0.1"))
                .doesNotThrowAnyException();
        assertThatCode(() -> service.recordLoginFailure("user@example.com", "127.0.0.1"))
                .doesNotThrowAnyException();
    }

}
