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

/**
 * JWT 令牌服务。
 *
 * <p>负责 Access Token 和 Refresh Token 的创建与解析。
 * Token 中包含用户 ID、Token 版本等声明信息，用于身份认证和状态校验。
 * 使用 HMAC-SHA 算法签名，密钥来自 {@link JwtProperties} 配置。</p>
 */
@Service
public class JwtTokenService {

    /** Token 版本声明字段名 */
    private static final String TOKEN_VERSION_CLAIM = "ver";

    private final JwtProperties jwtProperties;
    private final SecretKey signingKey;

    public JwtTokenService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        this.signingKey = Keys.hmacShaKeyFor(jwtProperties.jwtSecret().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 创建 Access Token。
     *
     * @param user         用户实体
     * @param tokenVersion 当前 Token 版本号
     * @return 签名后的 JWT 字符串
     */
    public String createAccessToken(User user, long tokenVersion) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(accessTokenTtlSeconds());
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(user.getId()))
                .claim(TOKEN_VERSION_CLAIM, tokenVersion)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();
    }

    /**
     * 创建 Refresh Token。
     *
     * @param user         用户实体
     * @param tokenVersion 当前 Token 版本号
     * @return 签名后的 JWT 字符串，包含 type=refresh 声明
     */
    public String createRefreshToken(User user, long tokenVersion) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(refreshTokenTtlSeconds());
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(user.getId()))
                .claim(TOKEN_VERSION_CLAIM, tokenVersion)
                .claim("type", "refresh")
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();
    }

    /** 解析 Access Token 并提取用户 ID */
    public Long parseUserId(String token) {
        return parseAccessToken(token).userId();
    }

    /** 解析 Refresh Token 并提取用户 ID */
    public Long parseRefreshUserId(String token) {
        return parseRefreshToken(token).userId();
    }

    /**
     * 解析 Access Token，拒绝 Refresh Token。
     *
     * @param token JWT 字符串
     * @return 解析后的 Token 声明
     * @throws BizException Token 无效或为 Refresh Token 时抛出
     */
    public TokenClaims parseAccessToken(String token) {
        Claims claims = parseClaims(token);
        if ("refresh".equals(claims.get("type", String.class))) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return toTokenClaims(claims);
    }

    /**
     * 解析 Refresh Token，拒绝 Access Token。
     *
     * @param token JWT 字符串
     * @return 解析后的 Token 声明
     * @throws BizException Token 无效或为 Access Token 时抛出
     */
    public TokenClaims parseRefreshToken(String token) {
        Claims claims = parseClaims(token);
        if (!"refresh".equals(claims.get("type", String.class))) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return toTokenClaims(claims);
    }

    /** 获取 Access Token 的有效期（秒） */
    public long accessTokenTtlSeconds() {
        return jwtProperties.accessTokenTtlMinutes() * 60;
    }

    /** 获取 Refresh Token 的有效期（秒） */
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
        long tokenVersion = tokenVersion(claims);
        Date expiration = claims.getExpiration();
        return new TokenClaims(
                Long.valueOf(claims.getSubject()),
                tokenId,
                expiration == null ? null : expiration.toInstant(),
                tokenVersion
        );
    }

    private long tokenVersion(Claims claims) {
        Object version = claims.get(TOKEN_VERSION_CLAIM);
        if (!(version instanceof Number number) || number.longValue() <= 0) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return number.longValue();
    }

    /**
     * Token 声明信息记录。
     *
     * @param userId       用户 ID
     * @param tokenId      Token 唯一标识（JTI）
     * @param expiresAt    过期时间
     * @param tokenVersion Token 版本号
     */
    public record TokenClaims(Long userId, String tokenId, Instant expiresAt, Long tokenVersion) {
    }
}
