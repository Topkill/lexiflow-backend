package com.lexiflow.report.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;

/**
 * 学习报告列表分页查询请求。
 *
 * @param page      页码，默认 1
 * @param size      每页数量，默认 20，最大 100
 * @param startDate 开始日期（可选）
 * @param endDate   结束日期（可选）
 */
@Schema(description = "学习报告列表查询")
public record ReportQueryRequest(
        @Schema(description = "页码") @Min(1) Long page,
        @Schema(description = "每页数量") @Min(1) @Max(100) Long size,
        @Schema(description = "开始日期") LocalDate startDate,
        @Schema(description = "结束日期") LocalDate endDate
) {
    public ReportQueryRequest {
        page = page == null ? 1L : page;
        size = size == null ? 20L : size;
    }
}
