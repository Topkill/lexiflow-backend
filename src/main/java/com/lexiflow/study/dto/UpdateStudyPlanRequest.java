package com.lexiflow.study.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 更新学习计划请求。
 *
 * @param name                计划名称
 * @param newWordsPerGroup    每组新词数量
 * @param reviewWordsPerGroup 每组复习词数量
 */
@Schema(description = "更新学习计划请求")
public record UpdateStudyPlanRequest(
        @Schema(description = "计划名称", example = "CET4 核心词计划")
        @NotBlank
        @Size(max = 128)
        String name,

        @Schema(description = "每组新词数量", example = "20")
        @NotNull
        @Min(1)
        @Max(300)
        Integer newWordsPerGroup,

        @Schema(description = "每组复习词数量", example = "40")
        @NotNull
        @Min(0)
        @Max(600)
        Integer reviewWordsPerGroup
) {
}
