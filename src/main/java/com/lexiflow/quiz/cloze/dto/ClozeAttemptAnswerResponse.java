package com.lexiflow.quiz.cloze.dto;

import com.lexiflow.quiz.cloze.domain.ClozeAttemptAnswer;
import com.lexiflow.quiz.cloze.domain.ClozeQuizBlank;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "完形填空作答明细响应")
public record ClozeAttemptAnswerResponse(
        @Schema(description = "空格 ID") String blankId,
        @Schema(description = "用户答案") String userAnswer,
        @Schema(description = "正确答案") String correctAnswer,
        @Schema(description = "是否正确") Boolean correct,
        @Schema(description = "正确答案本题使用词性") String correctAnswerPos,
        @Schema(description = "正确答案本题使用中文释义") String correctDefinitionZh,
        @Schema(description = "正确答案全部词性释义") List<ClozeDefinitionGroupResponse> correctDefinitions,
        @Schema(description = "选择原因") String reasonZh,
        @Schema(description = "解析") String explanation
) {
    public static ClozeAttemptAnswerResponse of(
            ClozeAttemptAnswer answer,
            ClozeQuizBlank blank,
            String correctAnswerPos,
            String correctDefinitionZh,
            List<ClozeDefinitionGroupResponse> correctDefinitions,
            String reasonZh
    ) {
        return new ClozeAttemptAnswerResponse(
                String.valueOf(answer.getBlankId()),
                answer.getUserAnswer(),
                answer.getCorrectAnswer(),
                answer.getCorrect(),
                correctAnswerPos,
                correctDefinitionZh,
                correctDefinitions,
                reasonZh,
                blank == null ? null : blank.getExplanation()
        );
    }
}
