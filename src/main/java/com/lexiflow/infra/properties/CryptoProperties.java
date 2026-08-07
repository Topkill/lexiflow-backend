package com.lexiflow.infra.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 加密配置属性。
 * <p>
 * 对应配置前缀 {@code lexiflow.crypto}，用于配置加解密相关参数。
 * </p>
 *
 * @param apiKeySecret API Key 加解密密钥
 */
@ConfigurationProperties(prefix = "lexiflow.crypto")
public record CryptoProperties(String apiKeySecret) {
}
