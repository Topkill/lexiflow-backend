package com.lexiflow.async.dto;

import com.lexiflow.async.domain.AsyncTask;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "异步任务状态响应")
public record AsyncTaskResponse(
        @Schema(description = "任务 ID") String taskId,
        @Schema(description = "任务类型") String taskType,
        @Schema(description = "任务状态") String status,
        @Schema(description = "进度") Integer progress,
        @Schema(description = "状态消息") String message,
        @Schema(description = "结果 ID") String resultId,
        @Schema(description = "错误码") String errorCode,
        @Schema(description = "错误信息") String errorMessage,
        @Schema(description = "创建时间") LocalDateTime createdAt
) {
    public static AsyncTaskResponse from(AsyncTask task) {
        return new AsyncTaskResponse(
                String.valueOf(task.getId()),
                task.getTaskType().name(),
                task.getStatus().name(),
                task.getProgress(),
                task.getMessage(),
                task.getResultId() == null ? null : String.valueOf(task.getResultId()),
                task.getErrorCode(),
                task.getErrorMessage(),
                task.getCreatedAt()
        );
    }
}
