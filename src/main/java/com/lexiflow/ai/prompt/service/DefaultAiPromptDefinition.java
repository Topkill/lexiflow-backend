package com.lexiflow.ai.prompt.service;

import com.lexiflow.ai.prompt.domain.AiPromptFeatureType;

/**
 * 内置 AI 提示词定义
 * <p>
 * 描述系统内置的 AI 提示词模板，包含功能类型、模板键、名称、系统提示词、规则提示词和输出 Schema。
 * </p>
 */
record DefaultAiPromptDefinition(
        AiPromptFeatureType featureType,
        String templateKey,
        String name,
        String systemPrompt,
        String instructionPrompt,
        String outputSchemaJson
) {
}
