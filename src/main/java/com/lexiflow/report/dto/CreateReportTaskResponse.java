package com.lexiflow.report.dto;

import com.lexiflow.async.domain.AsyncTask;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "创建 AI 学习报告任务响应")
public record CreateReportTaskResponse(
        @Schema(description = "任务 ID") String taskId,
        @Schema(description = "任务状态") String status,
        @Schema(description = "结果 ID，成功后为报告 ID") String resultId
) {
    public static CreateReportTaskResponse from(AsyncTask task) {
        return new CreateReportTaskResponse(
                String.valueOf(task.getId()),
                task.getStatus().name(),
                task.getResultId() == null ? null : String.valueOf(task.getResultId())
        );
    }
}