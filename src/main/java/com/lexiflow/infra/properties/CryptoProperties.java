package com.lexiflow.infra.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "lexiflow.crypto")
public record CryptoProperties(String apiKeySecret) {
}
