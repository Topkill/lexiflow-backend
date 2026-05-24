package com.lexiflow.ai.prompt.service;

import com.lexiflow.ai.prompt.domain.AiPromptFeatureType;

public record ResolvedAiPromptTemplate(
        AiPromptFeatureType featureType,
        Long templateId,
        String templateName,
        String systemPrompt,
        String instructionPrompt,
        boolean builtIn,
        String cacheFingerprint
) {
}
