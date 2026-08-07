package com.lexiflow.report.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;

/**
 * 创建 AI 学习报告任务请求。
 *
 * @param dailyTaskId 今日任务 ID
 * @param reportDate  报告日期
 */
@Schema(description = "创建 AI 学习报告任务请求")
public record CreateReportTaskRequest(
        @Schema(description = "今日任务 ID") @NotNull @Positive Long dailyTaskId,
        @Schema(description = "报告日期") @NotNull @PastOrPresent LocalDate reportDate
) {
}