package com.lexiflow.auth.service;

import com.lexiflow.infra.redis.RedisKeys;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * JWT 撤销服务。
 *
 * <p>基于 Redis 实现 Access Token 和 Refresh Token 的撤销与检查。
 * 撤销信息以 Token ID 为键存储，TTL 与 Token 剩余有效期一致，
 * Token 过期后自动清除撤销记录。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class JwtRevocationService {

    private final StringRedisTemplate stringRedisTemplate;

    /** 撤销 Access Token */
    public void revokeAccessToken(String tokenId, Instant expiresAt) {
        if (!StringUtils.hasText(tokenId)) {
            return;
        }
        revoke(RedisKeys.authRevokedAccessTokenKey(tokenId), tokenId, expiresAt);
    }

    /** 撤销 Refresh Token */
    public void revokeRefreshToken(String tokenId, Instant expiresAt) {
        if (!StringUtils.hasText(tokenId)) {
            return;
        }
        revoke(RedisKeys.authRevokedRefreshTokenKey(tokenId), tokenId, expiresAt);
    }

    /** 检查 Access Token 是否已被撤销 */
    public boolean isAccessTokenRevoked(String tokenId) {
        if (!StringUtils.hasText(tokenId)) {
            return false;
        }
        return isRevoked(RedisKeys.authRevokedAccessTokenKey(tokenId), tokenId);
    }

    /** 检查 Refresh Token 是否已被撤销 */
    public boolean isRefreshTokenRevoked(String tokenId) {
        if (!StringUtils.hasText(tokenId)) {
            return false;
        }
        return isRevoked(RedisKeys.authRevokedRefreshTokenKey(tokenId), tokenId);
    }

    private void revoke(String key, String tokenId, Instant expiresAt) {
        if (expiresAt == null) {
            return;
        }
        Duration ttl = Duration.between(Instant.now(), expiresAt);
        if (ttl.isNegative() || ttl.isZero()) {
            return;
        }
        try {
            stringRedisTemplate.opsForValue().set(key, "1", ttl);
        } catch (RuntimeException ex) {
            log.warn("Redis jwt revoke failed, tokenId={}", tokenId, ex);
        }
    }

    private boolean isRevoked(String key, String tokenId) {
        try {
            return Boolean.TRUE.equals(stringRedisTemplate.hasKey(key));
        } catch (RuntimeException ex) {
            log.warn("Redis jwt revocation check failed, tokenId={}", tokenId, ex);
            return false;
        }
    }
}
