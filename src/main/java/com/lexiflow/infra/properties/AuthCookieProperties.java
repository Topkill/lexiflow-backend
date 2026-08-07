package com.lexiflow.infra.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 认证 Cookie 配置属性。
 * <p>
 * 对应配置前缀 {@code lexiflow.auth.cookie}，用于配置认证相关 Cookie 的安全属性。
 * </p>
 *
 * @param secure   是否启用 Secure 标志（仅 HTTPS 传输）
 * @param sameSite SameSite 策略（如 Strict、Lax、None）
 * @param path     Cookie 路径
 */
@ConfigurationProperties(prefix = "lexiflow.auth.cookie")
public record AuthCookieProperties(
        boolean secure,
        String sameSite,
        String path
) {
}
