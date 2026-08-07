package com.lexiflow.ai.config.dto;

import com.lexiflow.ai.config.domain.UserAiConfig;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户私有 AI 配置响应 DTO
 * <p>
 * 用于返回用户个人 AI 配置信息，包含 API 地址、模型名称、温度参数、启用状态等。
 * 若用户未配置，可通过 {@link #empty()} 获取空响应对象。
 * </p>
 */
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
    /**
     * 返回空的配置响应，表示用户尚未配置 AI
     *
     * @return 未配置状态的用户 AI 配置响应
     */
    public static UserAiConfigResponse empty() {
        return new UserAiConfigResponse(false, null, null, null, false, false, null);
    }

    /**
     * 从实体对象转换为响应 DTO
     *
     * @param config 用户 AI 配置实体对象
     * @return 用户 AI 配置响应 DTO
     */
    public static UserAiConfigResponse from(UserAiConfig config) {
        return new UserAiConfigResponse(true, config.getApiBaseUrl(), config.getModelName(), config.getTemperature(), config.getStreamEnabled(), config.getEnabled(), config.getLastVerifiedAt());
    }
}
