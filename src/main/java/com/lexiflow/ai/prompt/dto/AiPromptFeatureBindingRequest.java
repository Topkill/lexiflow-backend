package com.lexiflow.ai.prompt.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * AI 功能提示词绑定请求 DTO
 * <p>
 * 用于设置某个 AI 功能类型在指定词书范围内使用的提示词模板。
 * </p>
 */
@Schema(description = "AI 功能提示词绑定请求")
public record AiPromptFeatureBindingRequest(
        @Schema(description = "生效词书 ID，0 表示全部词书") Long wordbookId,
        @Schema(description = "模板 ID，空表示恢复内置默认提示词") Long templateId
) {
}
