package com.lexiflow.system.config.dto;

import com.lexiflow.system.config.domain.SystemConfigValueType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "系统配置保存请求")
public record SystemConfigRequest(
        @Schema(description = "配置键") @NotBlank @Size(max = 128) String configKey,
        @Schema(description = "配置值") String configValue,
        @Schema(description = "配置值类型") @NotNull SystemConfigValueType valueType,
        @Schema(description = "配置说明") @Size(max = 512) String description,
        @Schema(description = "是否后台可编辑") @NotNull Boolean editable
) {
}
