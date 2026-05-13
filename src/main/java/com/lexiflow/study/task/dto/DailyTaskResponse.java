package com.lexiflow.study.task.dto;

import com.lexiflow.study.task.domain.DailyTask;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "今日任务响应")
public record DailyTaskResponse(
        @Schema(description = "任务 ID", example = "1900000000000005000") String taskId,
        @Schema(description = "任务日期", example = "2026-05-14") LocalDate taskDate,
        @Schema(description = "任务状态", example = "PENDING") String status,
        @Schema(description = "今日新词数量", example = "30") Integer newCount,
        @Schema(description = "到期复习数量", example = "0") Integer reviewCount,
        @Schema(description = "额外学习数量", example = "0") Integer extraCount,
        @Schema(description = "已完成数量", example = "0") Integer doneCount,
        @Schema(description = "跳过数量", example = "0") Integer skippedCount,
        @Schema(description = "计划摘要") DailyTaskPlanResponse plan,
        @Schema(description = "任务明细") List<DailyTaskItemResponse> items
) {
    public static DailyTaskResponse from(DailyTask task, DailyTaskPlanResponse plan, List<DailyTaskItemResponse> items) {
        return new DailyTaskResponse(
                String.valueOf(task.getId()),
                task.getTaskDate(),
                task.getStatus().name(),
                task.getNewCount(),
                task.getReviewCount(),
                task.getExtraCount(),
                task.getDoneCount(),
                task.getSkippedCount(),
                plan,
                items
        );
    }
}
