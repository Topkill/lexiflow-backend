package com.lexiflow.ai.config.dto;

import com.lexiflow.ai.config.domain.AiPublicConfig;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "公共 AI 配置响应")
public record AiPublicConfigResponse(
        @Schema(description = "配置 ID") String id,
        @Schema(description = "配置名称") String name,
        @Schema(description = "API Base URL") String apiBaseUrl,
        @Schema(description = "是否已配置 Key") Boolean keyConfigured,
        @Schema(description = "模型名称") String modelName,
        @Schema(description = "温度") BigDecimal temperature,
        @Schema(description = "是否使用流式输出") Boolean streamEnabled,
        @Schema(description = "每日配额") Integer dailyQuotaPerUser,
        @Schema(description = "是否激活") Boolean active,
        @Schema(description = "是否启用") Boolean enabled,
        @Schema(description = "备注") String remark,
        @Schema(description = "创建时间") LocalDateTime createdAt,
        @Schema(description = "更新时间") LocalDateTime updatedAt
) {
    public static AiPublicConfigResponse from(AiPublicConfig config) {
        return new AiPublicConfigResponse(
                String.valueOf(config.getId()),
                config.getName(),
                config.getApiBaseUrl(),
                config.getEncryptedApiKey() != null && !config.getEncryptedApiKey().isBlank(),
                config.getModelName(),
                config.getTemperature(),
                config.getStreamEnabled(),
                config.getDailyQuotaPerUser(),
                config.getActive(),
                config.getEnabled(),
                config.getRemark(),
                config.getCreatedAt(),
                config.getUpdatedAt()
        );
    }
}
