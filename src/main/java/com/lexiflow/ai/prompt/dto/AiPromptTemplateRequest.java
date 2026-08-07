package com.lexiflow.ai.prompt.dto;

import com.lexiflow.ai.prompt.domain.AiPromptFeatureType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * AI 提示词模板保存请求 DTO
 * <p>
 * 用于创建或编辑 AI 提示词模板，包含功能类型、词书范围、系统提示词、规则提示词和输出 Schema。
 * </p>
 */
@Schema(description = "AI 提示词模板保存请求")
public record AiPromptTemplateRequest(
        @Schema(description = "功能类型") @NotNull AiPromptFeatureType featureType,
        @Schema(description = "生效词书 ID，0 表示全部词书") Long wordbookId,
        @Schema(description = "模板名称") @NotBlank @Size(max = 128) String name,
        @Schema(description = "系统提示词") @NotBlank @Size(max = 12000) String systemPrompt,
        @Schema(description = "规则提示词") @NotBlank @Size(max = 20000) String instructionPrompt,
        @Schema(description = "输出 JSON 结构") @Size(max = 20000) String outputSchemaJson,
        @Schema(description = "是否启用") @NotNull Boolean enabled
) {
}
