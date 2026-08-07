package com.lexiflow.common.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 分页响应封装类。
 * <p>
 * 用于包装分页查询结果，包含数据列表、总记录数、当前页码和每页数量。
 * </p>
 *
 * @param <T>      数据列表元素的类型
 * @param records  当前页数据列表
 * @param total    总记录数
 * @param page     当前页码（从 1 开始）
 * @param size     每页数量
 */
@Schema(description = "分页响应")
public record PageResponse<T>(
        @Schema(description = "数据列表") List<T> records,
        @Schema(description = "总记录数", example = "128") long total,
        @Schema(description = "当前页码", example = "1") long page,
        @Schema(description = "每页数量", example = "20") long size
) {
    /**
     * 静态工厂方法，创建分页响应对象。
     *
     * @param records 数据列表
     * @param total   总记录数
     * @param page    当前页码
     * @param size    每页数量
     * @return 分页响应对象
     */
    public static <T> PageResponse<T> of(List<T> records, long total, long page, long size) {
        return new PageResponse<>(records, total, page, size);
    }
}
