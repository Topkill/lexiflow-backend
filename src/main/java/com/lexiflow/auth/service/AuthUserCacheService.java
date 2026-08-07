package com.lexiflow.auth.service;

import com.lexiflow.auth.security.AuthUser;
import com.lexiflow.infra.redis.RedisJsonCacheService;
import com.lexiflow.infra.redis.RedisKeys;
import com.lexiflow.user.domain.UserRole;
import com.lexiflow.user.domain.UserStatus;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 认证用户缓存服务。
 *
 * <p>基于 Redis 缓存 {@link AuthUser} 对象，TTL 为 2 分钟。
 * 通过 {@link AuthUserSnapshot} 中间快照实现实体与缓存数据的转换，
 * 仅缓存状态为 ACTIVE 的用户，非活跃用户自动清除缓存。</p>
 */
@Service
@RequiredArgsConstructor
public class AuthUserCacheService {

    /** 缓存 TTL：2 分钟 */
    static final Duration CACHE_TTL = Duration.ofMinutes(2);

    private final RedisJsonCacheService redisJsonCacheService;

    /**
     * 从缓存中获取认证用户。
     *
     * @param userId 用户 ID
     * @return 缓存的认证用户，缓存未命中或用户非活跃时返回 null
     */
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

    /**
     * 将认证用户写入缓存。
     *
     * <p>若用户状态非 ACTIVE，则仅清除缓存不写入新数据。</p>
     *
     * @param authUser 认证用户对象
     */
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

    /** 清除指定用户的认证缓存 */
    public void evict(Long userId) {
        if (userId == null) {
            return;
        }
        redisJsonCacheService.delete(RedisKeys.authUserKey(userId));
    }

    /**
     * 认证用户的 Redis 缓存快照。
     *
     * <p>作为 AuthUser 与 Redis JSON 之间的中间表示。</p>
     */
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
