package com.lexiflow.quiz.cloze.dto;

import com.lexiflow.quiz.cloze.domain.ClozeSourceType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Schema(description = "创建 AI 完形填空任务请求")
public record CreateClozeTaskRequest(
        @Schema(description = "今日任务 ID") @NotNull @Positive Long dailyTaskId,
        @Schema(description = "生成来源", example = "MIXED") ClozeSourceType sourceType,
        @Schema(description = "目标词数量，完成学习组模式固定按 10 个空处理", example = "10") @Min(5) @Max(10) Integer targetWordCount
) {
    public ClozeSourceType safeSourceType() {
        return sourceType == null ? ClozeSourceType.MIXED : sourceType;
    }

    public int safeTargetWordCount() {
        return safeSourceType() == ClozeSourceType.COMPLETED_GROUP ? 10 : targetWordCount == null ? 8 : targetWordCount;
    }
}
