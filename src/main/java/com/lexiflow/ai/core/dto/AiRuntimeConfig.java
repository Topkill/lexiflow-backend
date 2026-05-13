package com.lexiflow.ai.core.dto;

import java.math.BigDecimal;

public record AiRuntimeConfig(
        AiConfigScope scope,
        String apiBaseUrl,
        String apiKey,
        String modelName,
        BigDecimal temperature,
        Integer dailyQuotaPerUser
) {
}
