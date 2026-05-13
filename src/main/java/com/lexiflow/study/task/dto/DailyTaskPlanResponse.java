package com.lexiflow.study.task.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "今日任务计划摘要")
public record DailyTaskPlanResponse(
        @Schema(description = "计划 ID", example = "1900000000000004001") String id,
        @Schema(description = "词库名称", example = "CET4 核心词") String wordbookName
) {
}
