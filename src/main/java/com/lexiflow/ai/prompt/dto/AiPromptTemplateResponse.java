package com.lexiflow.ai.prompt.dto;

import com.lexiflow.ai.prompt.domain.AiPromptFeatureType;
import com.lexiflow.ai.prompt.domain.AiPromptTemplate;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/**
 * AI 提示词模板响应 DTO
 * <p>
 * 返回提示词模板的详细信息，支持自定义模板和内置默认模板两种构建方式。
 * </p>
 */
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
        @Schema(description = "输出 JSON 结构") String outputSchemaJson,
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
    /**
     * 从实体对象构建响应
     *
     * @param template 提示词模板实体
     * @param active 是否为当前生效模板
     * @return 提示词模板响应 DTO
     */
    public static AiPromptTemplateResponse from(AiPromptTemplate template, boolean active) {
        return from(template, active, template.getOutputSchemaJson());
    }

    /**
     * 从实体对象构建响应，可指定输出 Schema
     *
     * @param template 提示词模板实体
     * @param active 是否为当前生效模板
     * @param outputSchemaJson 输出 JSON Schema
     * @return 提示词模板响应 DTO
     */
    public static AiPromptTemplateResponse from(AiPromptTemplate template, boolean active, String outputSchemaJson) {
        return new AiPromptTemplateResponse(
                template.getId() == null ? null : String.valueOf(template.getId()),
                template.getId() == null ? "builtin:" + template.getSourceBuiltinKey() : "template:" + template.getId(),
                template.getFeatureType(),
                template.getFeatureType() == null ? null : template.getFeatureType().label(),
                template.getWordbookId() == null ? "0" : String.valueOf(template.getWordbookId()),
                template.getName(),
                template.getSystemPrompt(),
                template.getInstructionPrompt(),
                outputSchemaJson,
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

    /**
     * 构建内置默认模板的响应
     *
     * @param featureType 功能类型
     * @param name 模板名称
     * @param systemPrompt 系统提示词
     * @param instructionPrompt 规则提示词
     * @param outputSchemaJson 输出 JSON Schema
     * @param active 是否为当前生效模板
     * @param templateKey 模板键
     * @return 内置模板响应 DTO
     */
    public static AiPromptTemplateResponse builtin(AiPromptFeatureType featureType, String name, String systemPrompt, String instructionPrompt, String outputSchemaJson, boolean active, String templateKey) {
        return new AiPromptTemplateResponse(
                null,
                templateKey,
                featureType,
                featureType.label(),
                "0",
                name,
                systemPrompt,
                instructionPrompt,
                outputSchemaJson,
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
