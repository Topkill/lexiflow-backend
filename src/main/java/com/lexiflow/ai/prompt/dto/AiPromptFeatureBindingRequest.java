package com.lexiflow.ai.prompt.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "AI 功能提示词绑定请求")
public record AiPromptFeatureBindingRequest(
        @Schema(description = "生效词书 ID，0 表示全部词书") Long wordbookId,
        @Schema(description = "模板 ID，空表示恢复内置默认提示词") Long templateId
) {
}
