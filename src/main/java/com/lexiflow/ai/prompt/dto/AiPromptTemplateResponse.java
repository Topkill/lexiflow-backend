package com.lexiflow.ai.prompt.dto;

import com.lexiflow.ai.prompt.domain.AiPromptFeatureType;
import com.lexiflow.ai.prompt.domain.AiPromptTemplate;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "AI 提示词模板响应")
public record AiPromptTemplateResponse(
        @Schema(description = "模板 ID，内置默认模板为空") String id,
        @Schema(description = "模板键") String templateKey,
        @Schema(description = "功能类型") AiPromptFeatureType featureType,
        @Schema(description = "功能名称") String featureLabel,
        @Schema(description = "生效词书 ID，0 表示全部词书") String wordbookId,
        @Schema(description = "模板名称") String name,
        @Schema(description = "系统提示词") String systemPrompt,
        @Schema(description = "规则提示词") String instructionPrompt,
        @Schema(description = "是否内置默认模板") Boolean builtIn,
        @Schema(description = "是否可编辑") Boolean editable,
        @Schema(description = "是否可复制") Boolean copyable,
        @Schema(description = "是否当前使用") Boolean active,
        @Schema(description = "是否启用") Boolean enabled,
        @Schema(description = "来源模板 ID") String sourceTemplateId,
        @Schema(description = "来源内置模板键") String sourceBuiltinKey,
        @Schema(description = "创建时间") LocalDateTime createdAt,
        @Schema(description = "更新时间") LocalDateTime updatedAt
) {
    public static AiPromptTemplateResponse from(AiPromptTemplate template, boolean active) {
        return new AiPromptTemplateResponse(
                template.getId() == null ? null : String.valueOf(template.getId()),
                template.getId() == null ? "builtin:" + template.getSourceBuiltinKey() : "template:" + template.getId(),
                template.getFeatureType(),
                template.getFeatureType() == null ? null : template.getFeatureType().label(),
                template.getWordbookId() == null ? "0" : String.valueOf(template.getWordbookId()),
                template.getName(),
                template.getSystemPrompt(),
                template.getInstructionPrompt(),
                false,
                true,
                true,
                active,
                Boolean.TRUE.equals(template.getEnabled()),
                template.getSourceTemplateId() == null ? null : String.valueOf(template.getSourceTemplateId()),
                template.getSourceBuiltinKey(),
                template.getCreatedAt(),
                template.getUpdatedAt()
        );
    }

    public static AiPromptTemplateResponse builtin(AiPromptFeatureType featureType, String name, String systemPrompt, String instructionPrompt, boolean active, String templateKey) {
        return new AiPromptTemplateResponse(
                null,
                templateKey,
                featureType,
                featureType.label(),
                "0",
                name,
                systemPrompt,
                instructionPrompt,
                true,
                false,
                true,
                active,
                true,
                null,
                templateKey,
                null,
                null
        );
    }
}
