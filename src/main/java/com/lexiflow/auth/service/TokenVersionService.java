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

@Service
@RequiredArgsConstructor
@Slf4j
public class TokenVersionService {

    static final long DEFAULT_VERSION = 1L;

    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private final StringRedisTemplate stringRedisTemplate;
    private final UserMapper userMapper;

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
