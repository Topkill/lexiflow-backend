package com.lexiflow.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lexiflow.infra.redis.RedisKeys;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class JwtRevocationServiceTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    @Test
    void revokeAccessTokenShouldWriteDenylistWithRemainingTtl() {
        JwtRevocationService service = new JwtRevocationService(stringRedisTemplate);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        Instant expiresAt = Instant.now().plusSeconds(60);
        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);

        service.revokeAccessToken("access-jti", expiresAt);

        verify(valueOperations).set(
                eq(RedisKeys.authRevokedAccessTokenKey("access-jti")),
                eq("1"),
                ttlCaptor.capture()
        );
        assertThat(ttlCaptor.getValue()).isGreaterThan(Duration.ZERO);
        assertThat(ttlCaptor.getValue()).isLessThanOrEqualTo(Duration.ofSeconds(60));
    }

    @Test
    void isRefreshTokenRevokedShouldReadDenylist() {
        JwtRevocationService service = new JwtRevocationService(stringRedisTemplate);
        when(stringRedisTemplate.hasKey(RedisKeys.authRevokedRefreshTokenKey("refresh-jti"))).thenReturn(true);

        assertThat(service.isRefreshTokenRevoked("refresh-jti")).isTrue();
    }

    @Test
    void blankTokenIdShouldSkipRedis() {
        JwtRevocationService service = new JwtRevocationService(stringRedisTemplate);

        service.revokeAccessToken("", Instant.now().plusSeconds(60));
        service.revokeRefreshToken(null, Instant.now().plusSeconds(60));
        assertThat(service.isAccessTokenRevoked("")).isFalse();
        assertThat(service.isRefreshTokenRevoked(null)).isFalse();

        verifyNoInteractions(stringRedisTemplate);
    }

    @Test
    void redisUnavailableShouldFailOpen() {
        JwtRevocationService service = new JwtRevocationService(stringRedisTemplate);
        when(stringRedisTemplate.hasKey(anyString())).thenThrow(new RuntimeException("redis down"));
        when(stringRedisTemplate.opsForValue()).thenThrow(new RuntimeException("redis down"));

        assertThat(service.isAccessTokenRevoked("access-jti")).isFalse();
        assertThatCode(() -> service.revokeRefreshToken("refresh-jti", Instant.now().plusSeconds(60)))
                .doesNotThrowAnyException();
    }
}
