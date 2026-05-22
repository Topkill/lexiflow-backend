package com.lexiflow.quiz.cloze.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.lexiflow.quiz.cloze.domain.ClozeQuiz;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "完形填空题目详情响应")
public record ClozeQuizResponse(
        @Schema(description = "题目 ID") String quizId,
        @Schema(description = "词库 ID") String wordbookId,
        @Schema(description = "标题") String title,
        @Schema(description = "短文内容") String passage,
        @Schema(description = "候选词") JsonNode candidateWords,
        @Schema(description = "空格列表") List<ClozeBlankResponse> blanks
) {
    public static ClozeQuizResponse of(ClozeQuiz quiz, JsonNode candidateWords, List<ClozeBlankResponse> blanks) {
        return of(quiz, quiz.getWordbookId(), candidateWords, blanks);
    }

    public static ClozeQuizResponse of(ClozeQuiz quiz, Long wordbookId, JsonNode candidateWords, List<ClozeBlankResponse> blanks) {
        return new ClozeQuizResponse(
                String.valueOf(quiz.getId()),
                wordbookId == null ? null : String.valueOf(wordbookId),
                quiz.getTitle(),
                quiz.getPassage(),
                candidateWords,
                blanks
        );
    }
}
