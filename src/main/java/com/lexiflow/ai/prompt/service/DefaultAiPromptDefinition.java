package com.lexiflow.ai.prompt.service;

import com.lexiflow.ai.prompt.domain.AiPromptFeatureType;

record DefaultAiPromptDefinition(
        AiPromptFeatureType featureType,
        String templateKey,
        String name,
        String systemPrompt,
        String instructionPrompt
) {
}
