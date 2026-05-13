package com.lexiflow.infra.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "lexiflow.security")
public record JwtProperties(
        String jwtSecret,
        long accessTokenTtlMinutes,
        long refreshTokenTtlDays
) {
}
