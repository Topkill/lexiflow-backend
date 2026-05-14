package com.lexiflow.quiz.cloze.dto;

import com.lexiflow.quiz.cloze.domain.ClozeAttempt;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

@Schema(description = "完形填空作答结果响应")
public record ClozeAttemptResponse(
        @Schema(description = "作答 ID") String attemptId,
        @Schema(description = "分数") BigDecimal score,
        @Schema(description = "总空格数") Integer totalBlanks,
        @Schema(description = "正确数量") Integer correctCount,
        @Schema(description = "错误数量") Integer wrongCount,
        @Schema(description = "作答明细") List<ClozeAttemptAnswerResponse> answers
) {
    public static ClozeAttemptResponse of(ClozeAttempt attempt, List<ClozeAttemptAnswerResponse> answers) {
        return new ClozeAttemptResponse(
                String.valueOf(attempt.getId()),
                attempt.getScore(),
                attempt.getTotalBlanks(),
                attempt.getCorrectCount(),
                attempt.getWrongCount(),
                answers
        );
    }
}