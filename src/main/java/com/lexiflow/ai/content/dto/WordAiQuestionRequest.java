package com.lexiflow.ai.content.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * AI 单词问答请求 DTO
 * <p>
 * 用户对单词发起提问的请求对象，包含词库 ID、问题内容和是否重新生成标识。
 * </p>
 */
@Schema(description = "AI 单词问答请求")
public record WordAiQuestionRequest(
        @Schema(description = "词库 ID") @NotNull @Positive Long wordbookId,
        @Schema(description = "用户问题", example = "这个词的反义词有哪些？")
        @NotBlank
        @Size(max = 500, message = "问题最多 500 个字符")
        String question,
        @Schema(description = "是否重新生成", example = "false") Boolean regenerate
) {
    /**
     * 获取安全处理后的问题内容（去除首尾空白）
     *
     * @return 处理后的问题字符串
     */
    public String safeQuestion() {
        return question == null ? "" : question.trim();
    }

    /**
     * 判断是否需要重新生成
     *
     * @return 如果 regenerate 为 true 则返回 true
     */
    public boolean shouldRegenerate() {
        return Boolean.TRUE.equals(regenerate);
    }
}
