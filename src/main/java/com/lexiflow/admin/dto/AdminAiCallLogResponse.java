package com.lexiflow.admin.dto;

import com.lexiflow.ai.content.domain.AiCallLog;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "后台 AI 调用日志响应")
public record AdminAiCallLogResponse(
        @Schema(description = "日志 ID") String id,
        @Schema(description = "用户 ID") String userId,
        @Schema(description = "配置范围") String configScope,
        @Schema(description = "内容类型") String contentType,
        @Schema(description = "模型名称") String modelName,
        @Schema(description = "API Base URL") String apiBaseUrl,
        @Schema(description = "提示词功能类型") String promptFeatureType,
        @Schema(description = "提示词模板 ID") String promptTemplateId,
        @Schema(description = "提示词模板名称") String promptTemplateName,
        @Schema(description = "状态") String status,
        @Schema(description = "输入 Token") Integer promptTokens,
        @Schema(description = "输出 Token") Integer completionTokens,
        @Schema(description = "总 Token") Integer totalTokens,
        @Schema(description = "耗时毫秒") Integer latencyMs,
        @Schema(description = "错误码") String errorCode,
        @Schema(description = "错误信息") String errorMessage,
        @Schema(description = "创建时间") LocalDateTime createdAt
) {
    /**
     * 将领域对象 AiCallLog 转换为响应对象 AdminAiCallLogResponse。
     *
     * @param log 领域对象，包含 AI 调用日志的详细信息
     * @return 转换后的响应对象 AdminAiCallLogResponse，若输入为 null 则可能抛出异常（取决于构造函数实现）
     */
    public static AdminAiCallLogResponse from(AiCallLog log) {
        // 处理可能为 null 的字段，将其转换为字符串或保持 null，确保响应对象的字段格式一致
        return new AdminAiCallLogResponse(
                String.valueOf(log.getId()),
                log.getUserId() == null ? null : String.valueOf(log.getUserId()),
                log.getConfigScope().name(),
                log.getContentType().name(),
                log.getModelName(),
                log.getApiBaseUrl(),
                log.getPromptFeatureType(),
                log.getPromptTemplateId() == null ? null : String.valueOf(log.getPromptTemplateId()),
                log.getPromptTemplateName(),
                log.getStatus().name(),
                log.getPromptTokens(),
                log.getCompletionTokens(),
                log.getTotalTokens(),
                log.getLatencyMs(),
                log.getErrorCode(),
                log.getErrorMessage(),
                log.getCreatedAt()
        );
    }
}
