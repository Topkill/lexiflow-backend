package com.lexiflow.auth.service;

import com.lexiflow.auth.security.AuthUser;
import com.lexiflow.infra.redis.RedisJsonCacheService;
import com.lexiflow.infra.redis.RedisKeys;
import com.lexiflow.user.domain.UserRole;
import com.lexiflow.user.domain.UserStatus;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthUserCacheService {

    static final Duration CACHE_TTL = Duration.ofMinutes(2);

    private final RedisJsonCacheService redisJsonCacheService;

    public AuthUser get(Long userId) {
        if (userId == null) {
            return null;
        }
        AuthUserSnapshot snapshot = redisJsonCacheService.get(RedisKeys.authUserKey(userId), AuthUserSnapshot.class);
        if (snapshot == null
                || !userId.equals(snapshot.id())
                || snapshot.role() == null
                || snapshot.status() != UserStatus.ACTIVE) {
            return null;
        }
        return snapshot.toAuthUser();
    }

    public void put(AuthUser authUser) {
        if (authUser == null || authUser.id() == null) {
            return;
        }
        if (authUser.role() == null || authUser.status() != UserStatus.ACTIVE) {
            evict(authUser.id());
            return;
        }
        redisJsonCacheService.set(RedisKeys.authUserKey(authUser.id()), AuthUserSnapshot.from(authUser), CACHE_TTL);
    }

    public void evict(Long userId) {
        if (userId == null) {
            return;
        }
        redisJsonCacheService.delete(RedisKeys.authUserKey(userId));
    }

    public record AuthUserSnapshot(
            Long id,
            String email,
            String nickname,
            UserRole role,
            UserStatus status
    ) {

        static AuthUserSnapshot from(AuthUser authUser) {
            return new AuthUserSnapshot(
                    authUser.id(),
                    authUser.email(),
                    authUser.nickname(),
                    authUser.role(),
                    authUser.status()
            );
        }

        AuthUser toAuthUser() {
            return new AuthUser(id, email, nickname, role, status);
        }
    }
}
