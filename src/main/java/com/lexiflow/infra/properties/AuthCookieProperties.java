package com.lexiflow.infra.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "lexiflow.auth.cookie")
public record AuthCookieProperties(
        boolean secure,
        String sameSite,
        String path
) {
}
