package com.lexiflow.quiz.cloze.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 提交完形填空答案请求 DTO。
 * <p>用于提交完形填空的作答结果。</p>
 *
 * @param durationSeconds 作答耗时秒数
 * @param answers 答案列表
 */
@Schema(description = "提交完形填空答案请求")
public record SubmitClozeAttemptRequest(
        @Schema(description = "作答耗时秒数") @PositiveOrZero Integer durationSeconds,
        @Schema(description = "答案列表") @Valid @NotEmpty @Size(max = 20) List<AnswerRequest> answers
) {
    public record AnswerRequest(
            @Schema(description = "空格 ID") @NotNull @Positive Long blankId,
            @Schema(description = "用户答案") @Size(max = 128) String answer
    ) {
    }
}