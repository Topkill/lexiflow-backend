package com.lexiflow.study.task.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 任务进度响应 DTO。
 * <p>包含已完成数、总数和完成率百分比。</p>
 */
@Schema(description = "任务进度响应")
public record TaskProgressResponse(
        @Schema(description = "已完成数量", example = "1") Integer doneCount,
        @Schema(description = "任务总数量", example = "42") Integer totalCount,
        @Schema(description = "完成率百分比", example = "50") Integer completionRate
) {
    /** 从已完成数和总数构建进度响应，自动计算完成率百分比。 */
    public static TaskProgressResponse from(Integer doneCount, Integer totalCount) {
        int safeDone = doneCount == null ? 0 : doneCount;
        int safeTotal = totalCount == null ? 0 : totalCount;
        int completionRate = safeTotal <= 0 ? 0 : Math.min(100, Math.round(safeDone * 100.0f / safeTotal));
        return new TaskProgressResponse(safeDone, safeTotal, completionRate);
    }
}
