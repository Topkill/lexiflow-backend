package com.lexiflow.admin.wordbook.dto;

import com.lexiflow.wordbook.domain.WordbookType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Schema(description = "后台词库分页查询")
public record AdminWordbookQueryRequest(
        @Schema(description = "页码") @Min(1) Long page,
        @Schema(description = "每页数量") @Min(1) @Max(100) Long size,
        @Schema(description = "词库类型") WordbookType type,
        @Schema(description = "是否启用") Boolean enabled,
        @Schema(description = "关键词") String keyword
) {
    public long safePage() {
        return page == null ? 1L : page;
    }

    public long safeSize() {
        return size == null ? 20L : size;
    }
}