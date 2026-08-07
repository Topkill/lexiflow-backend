package com.lexiflow.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 收藏单词请求。
 *
 * @param wordbookId 词库 ID
 * @param wordId     单词 ID
 * @param note       备注（可选）
 */
@Schema(description = "收藏单词请求")
public record FavoriteWordRequest(
        @Schema(description = "词库 ID") @NotNull @Positive Long wordbookId,
        @Schema(description = "单词 ID") @NotNull @Positive Long wordId,
        @Schema(description = "备注") @Size(max = 512) String note
) {
}
