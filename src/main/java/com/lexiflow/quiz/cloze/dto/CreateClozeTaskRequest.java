package com.lexiflow.quiz.cloze.dto;

import com.lexiflow.quiz.cloze.domain.ClozeSourceType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 创建 AI 完形填空任务请求 DTO。
 * <p>用于请求生成完形填空题目。</p>
 *
 * @param dailyTaskId 今日任务ID
 * @param sourceType 生成来源，默认为 MIXED
 * @param targetWordCount 目标词数量，默认 10，最多 10 个空
 * @param regenerate 是否强制重新生成，true 时跳过缓存
 */
@Schema(description = "创建 AI 完形填空任务请求")
public record CreateClozeTaskRequest(
        @Schema(description = "今日任务 ID") @NotNull @Positive Long dailyTaskId,
        @Schema(description = "生成来源", example = "MIXED") ClozeSourceType sourceType,
        @Schema(description = "目标词数量，默认 10，最多 10 个空", example = "10") @Min(5) @Max(10) Integer targetWordCount,
        @Schema(description = "是否强制重新生成，true 时跳过缓存", example = "false") Boolean regenerate
) {
    public CreateClozeTaskRequest {
        sourceType = sourceType == null ? ClozeSourceType.MIXED : sourceType;
        targetWordCount = targetWordCount == null ? 10 : targetWordCount;
        regenerate = Boolean.TRUE.equals(regenerate);
    }
}
