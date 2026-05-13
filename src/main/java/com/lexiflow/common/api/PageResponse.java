package com.lexiflow.common.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "分页响应")
public record PageResponse<T>(
        @Schema(description = "数据列表") List<T> records,
        @Schema(description = "总记录数", example = "128") long total,
        @Schema(description = "当前页码", example = "1") long page,
        @Schema(description = "每页数量", example = "20") long size
) {
    public static <T> PageResponse<T> of(List<T> records, long total, long page, long size) {
        return new PageResponse<>(records, total, page, size);
    }
}
