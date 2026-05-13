package com.lexiflow.study.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

@Schema(description = "创建学习计划请求")
public record CreateStudyPlanRequest(
        @Schema(description = "词库 ID", example = "1900000000000001001")
        @NotNull
        @Positive
        Long wordbookId,

        @Schema(description = "计划名称", example = "CET4 核心词计划")
        @NotBlank
        @Size(max = 128)
        String name,

        @Schema(description = "每日新词数量", example = "30")
        @NotNull
        @Min(1)
        @Max(300)
        Integer dailyNewWords,

        @Schema(description = "计划开始日期", example = "2026-05-14")
        @NotNull
        LocalDate startDate,

        @Schema(description = "是否主计划，空值默认 true", example = "true")
        Boolean isPrimary
) {
    public boolean primaryOrDefault() {
        return isPrimary == null || isPrimary;
    }
}
