package com.lexiflow.wordbook.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

@Schema(description = "词库单词查询参数")
public record WordQueryRequest(
        @Schema(description = "当前页码", example = "1") @Min(1) Long page,
        @Schema(description = "每页数量", example = "20") @Min(1) @Max(100) Long size,
        @Schema(description = "单词或释义关键字", example = "ability") @Size(max = 64) String keyword
) {
    public long safePage() {
        return page == null ? 1 : page;
    }

    public long safeSize() {
        return size == null ? 20 : size;
    }
}
