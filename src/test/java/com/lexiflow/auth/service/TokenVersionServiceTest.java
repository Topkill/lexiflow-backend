package com.lexiflow.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.lexiflow.auth.security.JwtTokenService.TokenClaims;
import com.lexiflow.infra.redis.RedisKeys;
import com.lexiflow.user.domain.User;
import com.lexiflow.user.mapper.UserMapper;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class TokenVersionServiceTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private UserMapper userMapper;

    @Test
    void currentVersionShouldReadDatabaseAndRefreshCache() {
        TokenVersionService service = tokenVersionService();
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(userMapper.selectById(7L)).thenReturn(userWithTokenVersion(3L));

        assertThat(service.currentVersion(7L)).isEqualTo(3L);
        verify(valueOperations).set(eq(RedisKeys.authTokenVersionKey(7L)), eq("3"), any(Duration.class));
    }

    @Test
    void currentVersionShouldDefaultWhenUserMissing() {
        TokenVersionService service = tokenVersionService();
        when(userMapper.selectById(7L)).thenReturn(null);

        assertThat(service.currentVersion(7L)).isEqualTo(1L);
    }

    @Test
    void isCurrentShouldCompareClaimVersionWithStoredVersion() {
        TokenVersionService service = tokenVersionService();
        TokenClaims claims = new TokenClaims(7L, "access-jti", Instant.now().plusSeconds(60), 3L);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(RedisKeys.authTokenVersionKey(7L))).thenReturn("3", "4");
        assertThat(service.isCurrent(claims)).isTrue();
        assertThat(service.isCurrent(claims)).isFalse();
    }

    @Test
    void isCurrentShouldFallbackToDatabaseWhenCacheMissing() {
        TokenVersionService service = tokenVersionService();
        TokenClaims claims = new TokenClaims(7L, "access-jti", Instant.now().plusSeconds(60), 3L);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(RedisKeys.authTokenVersionKey(7L))).thenReturn(null);
        when(userMapper.selectById(7L)).thenReturn(userWithTokenVersion(3L));

        assertThat(service.isCurrent(claims)).isTrue();
        verify(valueOperations).set(eq(RedisKeys.authTokenVersionKey(7L)), eq("3"), any(Duration.class));
    }

    @Test
    void isCurrentShouldRejectClaimsWithoutVersion() {
        TokenVersionService service = tokenVersionService();

        assertThat(service.isCurrent(new TokenClaims(7L, "access-jti", Instant.now().plusSeconds(60), null)))
                .isFalse();
        verifyNoInteractions(stringRedisTemplate);
        verifyNoInteractions(userMapper);
    }

    @Test
    void isCurrentShouldFallbackToDatabaseWhenRedisUnavailable() {
        TokenVersionService service = tokenVersionService();
        TokenClaims claims = new TokenClaims(7L, "access-jti", Instant.now().plusSeconds(60), 3L);
        when(stringRedisTemplate.opsForValue()).thenThrow(new RuntimeException("redis down"));
        when(userMapper.selectById(7L)).thenReturn(userWithTokenVersion(3L));

        assertThat(service.isCurrent(claims)).isTrue();
    }

    @Test
    void bumpVersionShouldUpdateDatabaseAndCacheNewVersion() {
        TokenVersionService service = tokenVersionService();
        when(userMapper.update(isNull(), ArgumentMatchers.<Wrapper<User>>any())).thenReturn(1);
        when(userMapper.selectById(7L)).thenReturn(userWithTokenVersion(2L));
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

        assertThat(service.bumpVersion(7L)).isEqualTo(2L);
        InOrder inOrder = inOrder(stringRedisTemplate, userMapper, valueOperations);
        inOrder.verify(stringRedisTemplate).delete(RedisKeys.authTokenVersionKey(7L));
        inOrder.verify(userMapper).update(isNull(), ArgumentMatchers.<Wrapper<User>>any());
        inOrder.verify(userMapper).selectById(7L);
        inOrder.verify(valueOperations).set(eq(RedisKeys.authTokenVersionKey(7L)), eq("2"), any(Duration.class));
    }

    @Test
    void bumpVersionShouldKeepCacheMissingWhenCacheWriteFails() {
        TokenVersionService service = tokenVersionService();
        when(userMapper.update(isNull(), ArgumentMatchers.<Wrapper<User>>any())).thenReturn(1);
        when(userMapper.selectById(7L)).thenReturn(userWithTokenVersion(2L));
        when(stringRedisTemplate.opsForValue()).thenThrow(new RuntimeException("redis down"));

        assertThat(service.bumpVersion(7L)).isEqualTo(2L);
        verify(stringRedisTemplate, times(2)).delete(RedisKeys.authTokenVersionKey(7L));
    }

    private TokenVersionService tokenVersionService() {
        return new TokenVersionService(stringRedisTemplate, userMapper);
    }

    private User userWithTokenVersion(Long tokenVersion) {
        User user = new User();
        user.setId(7L);
        user.setTokenVersion(tokenVersion);
        return user;
    }
}
