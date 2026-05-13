package com.lexiflow.ai.core.dto;

public record AiPrompt(
        String systemPrompt,
        String userPrompt,
        String requestHash
) {
}
