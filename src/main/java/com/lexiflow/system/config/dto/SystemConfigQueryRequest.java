package com.lexiflow.system.config.dto;

import com.lexiflow.system.config.domain.SystemConfigValueType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Schema(description = "系统配置分页查询")
public record SystemConfigQueryRequest(
        @Schema(description = "页码") @Min(1) Long page,
        @Schema(description = "每页数量") @Min(1) @Max(100) Long size,
        @Schema(description = "配置值类型") SystemConfigValueType valueType,
        @Schema(description = "是否可编辑") Boolean editable,
        @Schema(description = "关键词，匹配配置键或说明") String keyword
) {
    public long safePage() {
        return page == null ? 1L : page;
    }

    public long safeSize() {
        return size == null ? 20L : size;
    }
}
