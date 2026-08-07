package com.lexiflow.infra.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 安全配置属性。
 * <p>
 * 对应配置前缀 {@code lexiflow.security}，用于配置 JWT 相关参数。
 * </p>
 *
 * @param jwtSecret            JWT 签名密钥
 * @param accessTokenTtlMinutes 访问令牌有效期（分钟）
 * @param refreshTokenTtlDays   刷新令牌有效期（天）
 */
@ConfigurationProperties(prefix = "lexiflow.security")
public record JwtProperties(
        String jwtSecret,
        long accessTokenTtlMinutes,
        long refreshTokenTtlDays
) {
}
