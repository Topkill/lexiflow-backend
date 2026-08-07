package com.lexiflow.auth.service;

import com.lexiflow.auth.security.JwtTokenService.TokenClaims;
import com.lexiflow.infra.redis.RedisKeys;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Refresh Token 会话服务。
 *
 * <p>基于 Redis 管理 Refresh Token 的会话状态。
 * 登录时存储 Token 会话，刷新时检查会话是否存在且匹配，
 * 退出登录时删除会话记录。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenSessionService {

    private final StringRedisTemplate stringRedisTemplate;

    /** 存储 Refresh Token 会话，TTL 与 Token 剩余有效期一致 */
    public void store(TokenClaims claims) {
        if (claims == null || !StringUtils.hasText(claims.tokenId()) || claims.expiresAt() == null) {
            return;
        }
        Duration ttl = Duration.between(Instant.now(), claims.expiresAt());
        if (ttl.isNegative() || ttl.isZero()) {
            return;
        }
        try {
            stringRedisTemplate.opsForValue().set(
                    RedisKeys.authRefreshSessionKey(claims.tokenId()),
                    String.valueOf(claims.userId()),
                    ttl
            );
        } catch (RuntimeException ex) {
            log.warn("Redis refresh token session store failed, userId={}, tokenId={}", claims.userId(), claims.tokenId(), ex);
        }
    }

    /**
     * 获取 Refresh Token 会话状态。
     *
     * @param claims Token 声明信息
     * @return 会话状态：ACTIVE-有效，MISSING-不存在，UNAVAILABLE-Redis 不可用
     */
    public RefreshTokenSessionStatus getStatus(TokenClaims claims) {
        if (claims == null) {
            return RefreshTokenSessionStatus.MISSING;
        }
        if (!StringUtils.hasText(claims.tokenId())) {
            return RefreshTokenSessionStatus.MISSING;
        }
        try {
            String storedUserId = stringRedisTemplate.opsForValue().get(RedisKeys.authRefreshSessionKey(claims.tokenId()));
            if (!StringUtils.hasText(storedUserId)) {
                return RefreshTokenSessionStatus.MISSING;
            }
            return storedUserId.equals(String.valueOf(claims.userId()))
                    ? RefreshTokenSessionStatus.ACTIVE
                    : RefreshTokenSessionStatus.MISSING;
        } catch (RuntimeException ex) {
            log.warn("Redis refresh token session check failed, userId={}, tokenId={}", claims.userId(), claims.tokenId(), ex);
            return RefreshTokenSessionStatus.UNAVAILABLE;
        }
    }

    /** 删除 Refresh Token 会话 */
    public void delete(String tokenId) {
        if (!StringUtils.hasText(tokenId)) {
            return;
        }
        try {
            stringRedisTemplate.delete(RedisKeys.authRefreshSessionKey(tokenId));
        } catch (RuntimeException ex) {
            log.warn("Redis refresh token session delete failed, tokenId={}", tokenId, ex);
        }
    }
}
