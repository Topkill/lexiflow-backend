package com.lexiflow.auth.security;

import com.lexiflow.common.error.ErrorCode;
import com.lexiflow.common.exception.BizException;
import com.lexiflow.infra.properties.JwtProperties;
import com.lexiflow.user.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class JwtTokenService {

    private final JwtProperties jwtProperties;
    private final SecretKey signingKey;

    public JwtTokenService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        this.signingKey = Keys.hmacShaKeyFor(jwtProperties.jwtSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String createAccessToken(User user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(accessTokenTtlSeconds());
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(user.getId()))
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();
    }

    public String createRefreshToken(User user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(refreshTokenTtlSeconds());
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(user.getId()))
                .claim("type", "refresh")
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();
    }

    public Long parseUserId(String token) {
        return parseAccessToken(token).userId();
    }

    public Long parseRefreshUserId(String token) {
        return parseRefreshToken(token).userId();
    }

    public TokenClaims parseAccessToken(String token) {
        Claims claims = parseClaims(token);
        if ("refresh".equals(claims.get("type", String.class))) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return toTokenClaims(claims);
    }

    public TokenClaims parseRefreshToken(String token) {
        Claims claims = parseClaims(token);
        if (!"refresh".equals(claims.get("type", String.class))) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return toTokenClaims(claims);
    }

    public long accessTokenTtlSeconds() {
        return jwtProperties.accessTokenTtlMinutes() * 60;
    }

    public long refreshTokenTtlSeconds() {
        return jwtProperties.refreshTokenTtlDays() * 24 * 60 * 60;
    }

    private Claims parseClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException ex) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
    }

    private TokenClaims toTokenClaims(Claims claims) {
        String tokenId = claims.getId();
        if (!StringUtils.hasText(tokenId)) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        Date expiration = claims.getExpiration();
        return new TokenClaims(
                Long.valueOf(claims.getSubject()),
                tokenId,
                expiration == null ? null : expiration.toInstant()
        );
    }

    public record TokenClaims(Long userId, String tokenId, Instant expiresAt) {
    }
}
