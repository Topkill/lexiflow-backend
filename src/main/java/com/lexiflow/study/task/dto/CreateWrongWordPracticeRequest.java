package com.lexiflow.study.task.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

@Schema(description = "创建错词专项复习请求")
public record CreateWrongWordPracticeRequest(
        @Schema(description = "词库 ID，不传则使用当前计划词库") @Positive Long wordbookId,
        @Schema(description = "加入专项复习的错词数量", example = "10") @Min(1) @Max(50) Integer limit
) {
    public CreateWrongWordPracticeRequest {
        limit = limit == null ? 10 : limit;
    }
}
