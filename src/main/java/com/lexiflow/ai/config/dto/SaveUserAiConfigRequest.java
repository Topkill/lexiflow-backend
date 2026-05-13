package com.lexiflow.ai.config.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

@Schema(description = "保存用户私有 AI 配置请求")
public record SaveUserAiConfigRequest(
        @Schema(description = "OpenAI 兼容接口 Base URL") @NotBlank @Size(max = 512) String apiBaseUrl,
        @Schema(description = "API Key") @NotBlank @Size(max = 512) String apiKey,
        @Schema(description = "模型名称") @NotBlank @Size(max = 128) String modelName,
        @Schema(description = "温度") @NotNull @DecimalMin("0.00") @DecimalMax("2.00") BigDecimal temperature,
        @Schema(description = "是否启用") @NotNull Boolean enabled
) {
}
