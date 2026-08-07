package com.lexiflow.system.config.dto;

import com.lexiflow.system.config.domain.SystemConfig;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/**
 * 系统配置响应 DTO。
 * <p>包含配置项的基本信息，提供从实体转换的工厂方法。</p>
 */
@Schema(description = "系统配置响应")
public record SystemConfigResponse(
        @Schema(description = "配置 ID") String id,
        @Schema(description = "配置键") String configKey,
        @Schema(description = "配置值") String configValue,
        @Schema(description = "配置值类型") String valueType,
        @Schema(description = "配置说明") String description,
        @Schema(description = "是否后台可编辑") Boolean editable,
        @Schema(description = "创建时间") LocalDateTime createdAt,
        @Schema(description = "更新时间") LocalDateTime updatedAt
) {
    /** 从 SystemConfig 实体构建响应 DTO。 */
    public static SystemConfigResponse from(SystemConfig config) {
        return new SystemConfigResponse(
                String.valueOf(config.getId()),
                config.getConfigKey(),
                config.getConfigValue(),
                config.getValueType().name(),
                config.getDescription(),
                config.getEditable(),
                config.getCreatedAt(),
                config.getUpdatedAt()
        );
    }
}
