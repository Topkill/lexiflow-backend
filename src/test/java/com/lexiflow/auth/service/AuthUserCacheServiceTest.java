package com.lexiflow.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lexiflow.auth.security.AuthUser;
import com.lexiflow.infra.redis.RedisJsonCacheService;
import com.lexiflow.infra.redis.RedisKeys;
import com.lexiflow.user.domain.UserRole;
import com.lexiflow.user.domain.UserStatus;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthUserCacheServiceTest {

    @Mock
    private RedisJsonCacheService redisJsonCacheService;

    @Test
    void getShouldReturnCachedActiveAuthUser() {
        AuthUserCacheService service = new AuthUserCacheService(redisJsonCacheService);
        when(redisJsonCacheService.get(RedisKeys.authUserKey(7L), AuthUserCacheService.AuthUserSnapshot.class))
                .thenReturn(snapshot(7L, UserStatus.ACTIVE));

        AuthUser authUser = service.get(7L);

        assertThat(authUser).isNotNull();
        assertThat(authUser.id()).isEqualTo(7L);
        assertThat(authUser.role()).isEqualTo(UserRole.USER);
        assertThat(authUser.status()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void getShouldIgnoreMismatchedOrInactiveSnapshot() {
        AuthUserCacheService service = new AuthUserCacheService(redisJsonCacheService);
        when(redisJsonCacheService.get(RedisKeys.authUserKey(7L), AuthUserCacheService.AuthUserSnapshot.class))
                .thenReturn(snapshot(8L, UserStatus.ACTIVE), snapshot(7L, UserStatus.DISABLED));

        assertThat(service.get(7L)).isNull();
        assertThat(service.get(7L)).isNull();
    }

    @Test
    void putShouldWriteActiveAuthUserSnapshotWithTtl() {
        AuthUserCacheService service = new AuthUserCacheService(redisJsonCacheService);
        ArgumentCaptor<AuthUserCacheService.AuthUserSnapshot> snapshotCaptor =
                ArgumentCaptor.forClass(AuthUserCacheService.AuthUserSnapshot.class);
        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);

        service.put(authUser(UserStatus.ACTIVE));

        verify(redisJsonCacheService).set(
                eq(RedisKeys.authUserKey(7L)),
                snapshotCaptor.capture(),
                ttlCaptor.capture()
        );
        assertThat(snapshotCaptor.getValue().id()).isEqualTo(7L);
        assertThat(snapshotCaptor.getValue().status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(ttlCaptor.getValue()).isEqualTo(AuthUserCacheService.CACHE_TTL);
    }

    @Test
    void putShouldEvictInactiveAuthUser() {
        AuthUserCacheService service = new AuthUserCacheService(redisJsonCacheService);

        service.put(authUser(UserStatus.DISABLED));

        verify(redisJsonCacheService).delete(RedisKeys.authUserKey(7L));
    }

    @Test
    void evictShouldDeleteAuthUserKey() {
        AuthUserCacheService service = new AuthUserCacheService(redisJsonCacheService);

        service.evict(7L);

        verify(redisJsonCacheService).delete(RedisKeys.authUserKey(7L));
    }

    private AuthUserCacheService.AuthUserSnapshot snapshot(Long userId, UserStatus status) {
        return new AuthUserCacheService.AuthUserSnapshot(
                userId,
                "student@example.com",
                "Student",
                UserRole.USER,
                status
        );
    }

    private AuthUser authUser(UserStatus status) {
        return new AuthUser(
                7L,
                "student@example.com",
                "Student",
                UserRole.USER,
                status
        );
    }
}
