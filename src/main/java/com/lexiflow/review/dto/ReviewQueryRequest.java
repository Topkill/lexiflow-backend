package com.lexiflow.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

/**
 * 复习列表分页查询参数。
 *
 * @param wordbookId 词库 ID（可选）
 * @param page       页码，默认 1
 * @param size       每页数量，默认 20
 * @param sortBy     排序字段（如 wrongCount、lastWrongAt）
 * @param sortOrder  排序方向（asc/desc）
 */
@Schema(description = "复习列表查询参数")
public record ReviewQueryRequest(
        @Schema(description = "词库 ID") @Positive Long wordbookId,
        @Schema(description = "当前页码", example = "1") @Min(1) Long page,
        @Schema(description = "每页数量", example = "20") @Min(1) @Max(100) Long size,
        @Schema(description = "排序字段", example = "wrongCount") String sortBy,
        @Schema(description = "排序方向", example = "desc") String sortOrder
) {
    public ReviewQueryRequest {
        page = page == null ? 1L : page;
        size = size == null ? 20L : size;
    }
}
