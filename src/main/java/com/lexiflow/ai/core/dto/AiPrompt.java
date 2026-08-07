package com.lexiflow.ai.core.dto;

/**
 * AI 提示词对象
 * <p>
 * 封装发送给 AI 的系统提示词和用户提示词，以及提示词元数据（特性类型、模板信息等）。
 * </p>
 *
 * @param systemPrompt 系统提示词
 * @param userPrompt 用户提示词
 * @param requestHash 请求内容哈希，用于日志和缓存
 * @param promptFeatureType 提示词特性类型
 * @param promptTemplateId 提示词模板 ID
 * @param promptTemplateName 提示词模板名称
 */
public record AiPrompt(
        String systemPrompt,
        String userPrompt,
        String requestHash,
        String promptFeatureType,
        Long promptTemplateId,
        String promptTemplateName
) {
    /**
     * 构建不含模板元数据的提示词对象
     *
     * @param systemPrompt 系统提示词
     * @param userPrompt 用户提示词
     * @param requestHash 请求内容哈希
     */
    public AiPrompt(String systemPrompt, String userPrompt, String requestHash) {
        this(systemPrompt, userPrompt, requestHash, null, null, null);
    }
}
