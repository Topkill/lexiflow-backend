package com.lexiflow.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lexiflow.auth.security.JwtTokenService.TokenClaims;
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
class RefreshTokenSessionServiceTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    @Test
    void storeShouldWriteRefreshSessionWithRemainingTtl() {
        RefreshTokenSessionService service = new RefreshTokenSessionService(stringRedisTemplate);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        Instant expiresAt = Instant.now().plusSeconds(60);
        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);

        service.store(new TokenClaims(7L, "refresh-jti", expiresAt));

        verify(valueOperations).set(
                eq(RedisKeys.authRefreshSessionKey("refresh-jti")),
                eq("7"),
                ttlCaptor.capture()
        );
        assertThat(ttlCaptor.getValue()).isGreaterThan(Duration.ZERO);
        assertThat(ttlCaptor.getValue()).isLessThanOrEqualTo(Duration.ofSeconds(60));
    }

    @Test
    void getStatusShouldReturnActiveWhenStoredUserMatches() {
        RefreshTokenSessionService service = new RefreshTokenSessionService(stringRedisTemplate);
        TokenClaims claims = new TokenClaims(7L, "refresh-jti", Instant.now().plusSeconds(60));
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(RedisKeys.authRefreshSessionKey("refresh-jti"))).thenReturn("7");

        assertThat(service.getStatus(claims)).isEqualTo(RefreshTokenSessionStatus.ACTIVE);
    }

    @Test
    void getStatusShouldReturnMissingWhenSessionAbsentOrUserMismatch() {
        RefreshTokenSessionService service = new RefreshTokenSessionService(stringRedisTemplate);
        TokenClaims claims = new TokenClaims(7L, "refresh-jti", Instant.now().plusSeconds(60));
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(RedisKeys.authRefreshSessionKey("refresh-jti"))).thenReturn(null, "8");

        assertThat(service.getStatus(claims)).isEqualTo(RefreshTokenSessionStatus.MISSING);
        assertThat(service.getStatus(claims)).isEqualTo(RefreshTokenSessionStatus.MISSING);
    }

    @Test
    void getStatusShouldReturnUnavailableWhenRedisFails() {
        RefreshTokenSessionService service = new RefreshTokenSessionService(stringRedisTemplate);
        when(stringRedisTemplate.opsForValue()).thenThrow(new RuntimeException("redis down"));

        assertThat(service.getStatus(new TokenClaims(7L, "refresh-jti", Instant.now().plusSeconds(60))))
                .isEqualTo(RefreshTokenSessionStatus.UNAVAILABLE);
    }

    @Test
    void tokenWithoutJtiShouldBeMissingAndSkipRedis() {
        RefreshTokenSessionService service = new RefreshTokenSessionService(stringRedisTemplate);

        assertThat(service.getStatus(new TokenClaims(7L, null, Instant.now().plusSeconds(60))))
                .isEqualTo(RefreshTokenSessionStatus.MISSING);
        service.store(new TokenClaims(7L, "", Instant.now().plusSeconds(60)));
        service.delete(null);

        verifyNoInteractions(stringRedisTemplate);
    }

    @Test
    void nullClaimsShouldBeMissing() {
        RefreshTokenSessionService service = new RefreshTokenSessionService(stringRedisTemplate);

        assertThat(service.getStatus(null)).isEqualTo(RefreshTokenSessionStatus.MISSING);
    }

    @Test
    void deleteShouldDeleteRefreshSessionKey() {
        RefreshTokenSessionService service = new RefreshTokenSessionService(stringRedisTemplate);

        service.delete("refresh-jti");

        verify(stringRedisTemplate).delete(RedisKeys.authRefreshSessionKey("refresh-jti"));
    }

    @Test
    void redisFailuresShouldNotThrowWhenStoringOrDeleting() {
        RefreshTokenSessionService service = new RefreshTokenSessionService(stringRedisTemplate);
        when(stringRedisTemplate.opsForValue()).thenThrow(new RuntimeException("redis down"));
        when(stringRedisTemplate.delete(RedisKeys.authRefreshSessionKey("refresh-jti")))
                .thenThrow(new RuntimeException("redis down"));

        assertThatCode(() -> service.store(new TokenClaims(7L, "refresh-jti", Instant.now().plusSeconds(60))))
                .doesNotThrowAnyException();
        assertThatCode(() -> service.delete("refresh-jti"))
                .doesNotThrowAnyException();
    }
}
