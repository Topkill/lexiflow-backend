package com.lexiflow.ai.core.dto;

public record AiPrompt(
        String systemPrompt,
        String userPrompt,
        String requestHash,
        String promptFeatureType,
        Long promptTemplateId,
        String promptTemplateName
) {
    public AiPrompt(String systemPrompt, String userPrompt, String requestHash) {
        this(systemPrompt, userPrompt, requestHash, null, null, null);
    }
}
