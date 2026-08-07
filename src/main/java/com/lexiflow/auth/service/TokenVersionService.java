package com.lexiflow.auth.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.lexiflow.auth.security.JwtTokenService.TokenClaims;
import com.lexiflow.infra.redis.RedisKeys;
import com.lexiflow.user.domain.User;
import com.lexiflow.user.mapper.UserMapper;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Token 版本服务。
 *
 * <p>管理用户的 Token 版本号，用于 JWT 失效机制。
 * 修改密码等安全操作时递增版本号，使旧 Token 失效。
 * 版本号在 Redis 中缓存 5 分钟，减少数据库查询。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TokenVersionService {

    /** 默认 Token 版本号 */
    static final long DEFAULT_VERSION = 1L;

    /** 缓存 TTL：5 分钟 */
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private final StringRedisTemplate stringRedisTemplate;
    private final UserMapper userMapper;

    /**
     * 获取用户当前的 Token 版本号。
     *
     * @param userId 用户 ID
     * @return 当前版本号，用户不存在时返回默认版本
     */
    public long currentVersion(Long userId) {
        if (userId == null) {
            return DEFAULT_VERSION;
        }
        Long version = loadVersionFromDatabase(userId);
        if (version == null) {
            evictCache(userId);
            return DEFAULT_VERSION;
        }
        cacheVersion(userId, version);
        return version;
    }

    /**
     * 检查 Token 的版本号是否为当前版本。
     *
     * @param claims Token 声明信息
     * @return 版本号匹配时返回 true
     */
    public boolean isCurrent(TokenClaims claims) {
        if (claims == null || claims.userId() == null || claims.tokenVersion() == null) {
            return false;
        }
        try {
            Long currentVersion = currentVersionOrNull(claims.userId());
            return currentVersion != null && currentVersion.equals(claims.tokenVersion());
        } catch (RuntimeException ex) {
            log.warn("Token version check failed, userId={}", claims.userId(), ex);
            return false;
        }
    }

    /**
     * 递增用户的 Token 版本号。
     *
     * <p>通过 SQL 原子递增，并同步更新 Redis 缓存。</p>
     *
     * @param userId 用户 ID
     * @return 递增后的新版本号
     */
    public long bumpVersion(Long userId) {
        if (userId == null) {
            return DEFAULT_VERSION;
        }
        evictCache(userId);
        int updated = userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .setSql("token_version = COALESCE(token_version, 1) + 1"));
        if (updated <= 0) {
            evictCache(userId);
            return DEFAULT_VERSION;
        }
        Long nextVersion = loadVersionFromDatabase(userId);
        if (nextVersion == null) {
            evictCache(userId);
            return DEFAULT_VERSION;
        }
        if (!cacheVersion(userId, nextVersion)) {
            evictCache(userId);
        }
        return nextVersion;
    }

    private Long currentVersionOrNull(Long userId) {
        if (userId == null) {
            return null;
        }
        Long cachedVersion = cachedVersion(userId);
        if (cachedVersion != null) {
            return cachedVersion;
        }
        Long databaseVersion = loadVersionFromDatabase(userId);
        if (databaseVersion != null) {
            cacheVersion(userId, databaseVersion);
        }
        return databaseVersion;
    }

    private Long cachedVersion(Long userId) {
        try {
            return parseVersionOrNull(stringRedisTemplate.opsForValue().get(RedisKeys.authTokenVersionKey(userId)));
        } catch (RuntimeException ex) {
            log.warn("Redis token version read failed, userId={}", userId, ex);
            return null;
        }
    }

    private Long loadVersionFromDatabase(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return null;
        }
        Long version = user.getTokenVersion();
        return version != null && version > 0 ? version : DEFAULT_VERSION;
    }

    private boolean cacheVersion(Long userId, long tokenVersion) {
        try {
            stringRedisTemplate.opsForValue().set(RedisKeys.authTokenVersionKey(userId), String.valueOf(tokenVersion), CACHE_TTL);
            return true;
        } catch (RuntimeException ex) {
            log.warn("Redis token version write failed, userId={}", userId, ex);
            return false;
        }
    }

    private void evictCache(Long userId) {
        try {
            stringRedisTemplate.delete(RedisKeys.authTokenVersionKey(userId));
        } catch (RuntimeException ex) {
            log.warn("Redis token version evict failed, userId={}", userId, ex);
        }
    }

    private Long parseVersionOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            long version = Long.parseLong(value);
            return version > 0 ? version : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
