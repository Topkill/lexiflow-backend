package com.lexiflow.ai.prompt.service;

import com.lexiflow.ai.prompt.domain.AiPromptFeatureType;

/**
 * 已解析的 AI 提示词模板
 * <p>
 * 表示经过解析后最终生效的提示词模板信息，包含完整的系统提示词、规则提示词、
 * 输出 Schema 以及缓存指纹。可以是自定义模板或内置默认模板。
 * </p>
 *
 * @param featureType 功能类型
 * @param wordbookId 生效词书 ID
 * @param templateId 模板 ID，内置模板时为 null
 * @param templateName 模板名称
 * @param systemPrompt 系统提示词
 * @param instructionPrompt 规则提示词
 * @param outputSchemaJson 输出 JSON Schema
 * @param builtIn 是否为内置默认模板
 * @param cacheFingerprint 缓存指纹，用于检测模板变化
 */
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
