package com.lexiflow.ai.content.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Schema(description = "AI 单词问答请求")
public record WordAiQuestionRequest(
        @Schema(description = "词库 ID") @NotNull @Positive Long wordbookId,
        @Schema(description = "用户问题", example = "这个词的反义词有哪些？")
        @NotBlank
        @Size(max = 500, message = "问题最多 500 个字符")
        String question,
        @Schema(description = "是否重新生成", example = "false") Boolean regenerate
) {
    public String safeQuestion() {
        return question == null ? "" : question.trim();
    }

    public boolean shouldRegenerate() {
        return Boolean.TRUE.equals(regenerate);
    }
}
