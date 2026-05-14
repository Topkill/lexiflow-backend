package com.lexiflow.quiz.cloze.dto;

import com.lexiflow.async.domain.AsyncTask;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "创建 AI 完形填空任务响应")
public record CreateClozeTaskResponse(
        @Schema(description = "任务 ID") String taskId,
        @Schema(description = "任务状态") String status,
        @Schema(description = "结果 ID，成功后为完形填空题目 ID") String resultId
) {
    public static CreateClozeTaskResponse from(AsyncTask task) {
        return new CreateClozeTaskResponse(
                String.valueOf(task.getId()),
                task.getStatus().name(),
                task.getResultId() == null ? null : String.valueOf(task.getResultId())
        );
    }
}