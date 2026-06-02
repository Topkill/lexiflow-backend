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

@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenSessionService {

    private final StringRedisTemplate stringRedisTemplate;

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
