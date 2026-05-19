package com.lexiflow.study.task.dto;

import com.lexiflow.study.task.domain.DailyTask;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "今日任务响应")
public record DailyTaskResponse(
        @Schema(description = "任务 ID", example = "1900000000000005000") String taskId,
        @Schema(description = "任务日期", example = "2026-05-14") LocalDate taskDate,
        @Schema(description = "当天第几组学习任务", example = "2") Integer groupNo,
        @Schema(description = "任务状态", example = "PENDING") String status,
        @Schema(description = "本组新词数量", example = "20") Integer newCount,
        @Schema(description = "本组复习数量", example = "40") Integer reviewCount,
        @Schema(description = "额外学习数量", example = "0") Integer extraCount,
        @Schema(description = "已完成数量", example = "0") Integer doneCount,
        @Schema(description = "跳过数量", example = "0") Integer skippedCount,
        @Schema(description = "完成率百分比", example = "50") Integer completionRate,
        @Schema(description = "任务进度") TaskProgressResponse progress,
        @Schema(description = "计划摘要") DailyTaskPlanResponse plan,
        @Schema(description = "本组是否已生成完形填空", example = "true") Boolean clozeGenerated,
        @Schema(description = "本组是否已提交完形填空", example = "false") Boolean clozeAttempted,
        @Schema(description = "本组最近生成的完形填空题目 ID", example = "12") String clozeQuizId,
        @Schema(description = "任务明细") List<DailyTaskItemResponse> items
) {
    public static DailyTaskResponse from(
            DailyTask task,
            DailyTaskPlanResponse plan,
            List<DailyTaskItemResponse> items,
            boolean clozeGenerated,
            boolean clozeAttempted,
            Long clozeQuizId
    ) {
        TaskProgressResponse progress = TaskProgressResponse.from(task.getDoneCount(), totalCount(task));
        return new DailyTaskResponse(
                String.valueOf(task.getId()),
                task.getTaskDate(),
                task.getGroupNo(),
                task.getStatus().name(),
                task.getNewCount(),
                task.getReviewCount(),
                task.getExtraCount(),
                task.getDoneCount(),
                task.getSkippedCount(),
                progress.completionRate(),
                progress,
                plan,
                clozeGenerated,
                clozeAttempted,
                clozeQuizId == null ? null : String.valueOf(clozeQuizId),
                items
        );
    }

    private static int totalCount(DailyTask task) {
        return safeCount(task.getNewCount()) + safeCount(task.getReviewCount()) + safeCount(task.getExtraCount());
    }

    private static int safeCount(Integer count) {
        return count == null ? 0 : count;
    }
}
