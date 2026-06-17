package com.lexiflow.admin.wordbook.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Schema(description = "后台词库单词分页查询")
public record AdminWordQueryRequest(
        @Schema(description = "页码") @Min(1) Long page,
        @Schema(description = "每页数量") @Min(1) @Max(100) Long size,
        @Schema(description = "关键词") String keyword,
        @Schema(description = "是否启用") Boolean enabled
) {
    public AdminWordQueryRequest {
        page = page == null ? 1L : page;
        size = size == null ? 20L : size;
    }
}
