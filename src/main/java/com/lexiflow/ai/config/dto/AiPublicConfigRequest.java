package com.lexiflow.ai.config.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * 公共 AI 配置保存请求 DTO
 * <p>
 * 管理员用于创建或更新公共 AI 配置的请求对象，包含配置名称、API 地址、密钥、模型参数等信息。
 * </p>
 */
@Schema(description = "公共 AI 配置保存请求")
public record AiPublicConfigRequest(
        @Schema(description = "配置名称") @NotBlank @Size(max = 128) String name,
        @Schema(description = "API Base URL") @NotBlank @Size(max = 512) String apiBaseUrl,
        @Schema(description = "API Key，编辑时传入则覆盖") @Size(max = 512) String apiKey,
        @Schema(description = "模型名称") @NotBlank @Size(max = 128) String modelName,
        @Schema(description = "温度") @NotNull @DecimalMin("0.00") @DecimalMax("2.00") BigDecimal temperature,
        @Schema(description = "是否使用流式输出，不传则默认非流式") Boolean streamEnabled,
        @Schema(description = "每用户每日公共调用配额") @NotNull @Min(0) @Max(10000) Integer dailyQuotaPerUser,
        @Schema(description = "是否启用") @NotNull Boolean enabled,
        @Schema(description = "备注") @Size(max = 512) String remark
) {
}
