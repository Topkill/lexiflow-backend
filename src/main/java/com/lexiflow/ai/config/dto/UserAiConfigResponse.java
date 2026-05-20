package com.lexiflow.ai.config.dto;

import com.lexiflow.ai.config.domain.UserAiConfig;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "用户私有 AI 配置响应")
public record UserAiConfigResponse(
        @Schema(description = "是否已配置", example = "true") Boolean configured,
        @Schema(description = "API Base URL") String apiBaseUrl,
        @Schema(description = "模型名称") String modelName,
        @Schema(description = "温度") BigDecimal temperature,
        @Schema(description = "是否使用流式输出") Boolean streamEnabled,
        @Schema(description = "是否启用") Boolean enabled,
        @Schema(description = "最近验证时间") LocalDateTime lastVerifiedAt
) {
    public static UserAiConfigResponse empty() {
        return new UserAiConfigResponse(false, null, null, null, false, false, null);
    }

    public static UserAiConfigResponse from(UserAiConfig config) {
        return new UserAiConfigResponse(true, config.getApiBaseUrl(), config.getModelName(), config.getTemperature(), config.getStreamEnabled(), config.getEnabled(), config.getLastVerifiedAt());
    }
}
