package com.lexiflow.study.task.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "任务进度响应")
public record TaskProgressResponse(
        @Schema(description = "已完成数量", example = "1") Integer doneCount,
        @Schema(description = "任务总数量", example = "42") Integer totalCount
) {
}
