package com.lexiflow.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Schema(description = "收藏单词请求")
public record FavoriteWordRequest(
        @Schema(description = "词库 ID") @NotNull @Positive Long wordbookId,
        @Schema(description = "单词 ID") @NotNull @Positive Long wordId,
        @Schema(description = "备注") @Size(max = 512) String note
) {
}
