package com.lexiflow.ai.prompt.service;

import com.lexiflow.ai.prompt.domain.AiPromptFeatureType;

public record ResolvedAiPromptTemplate(
        AiPromptFeatureType featureType,
        Long wordbookId,
        Long templateId,
        String templateName,
        String systemPrompt,
        String instructionPrompt,
        String outputSchemaJson,
        boolean builtIn,
        String cacheFingerprint
) {
}
