package com.lexiflow.quiz.cloze.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.lexiflow.quiz.cloze.domain.ClozeQuiz;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 完形填空题目详情响应 DTO。
 * <p>返回完形填空题目的完整信息，包括短文、候选词、空格列表等。</p>
 *
 * @param quizId 题目ID
 * @param wordbookId 词库ID
 * @param title 标题
 * @param passage 短文内容
 * @param passageZh 短文中文翻译
 * @param candidateWords 候选词
 * @param blanks 空格列表
 * @param attempt 已提交作答结果
 */
@Schema(description = "完形填空题目详情响应")
public record ClozeQuizResponse(
        @Schema(description = "题目 ID") String quizId,
        @Schema(description = "词库 ID") String wordbookId,
        @Schema(description = "标题") String title,
        @Schema(description = "短文内容") String passage,
        @Schema(description = "短文中文翻译") String passageZh,
        @Schema(description = "候选词") JsonNode candidateWords,
        @Schema(description = "空格列表") List<ClozeBlankResponse> blanks,
        @Schema(description = "已提交作答结果") ClozeAttemptResponse attempt
) {
    public static ClozeQuizResponse of(ClozeQuiz quiz, JsonNode candidateWords, List<ClozeBlankResponse> blanks) {
        return of(quiz, quiz.getWordbookId(), candidateWords, blanks, null);
    }

    public static ClozeQuizResponse of(ClozeQuiz quiz, Long wordbookId, JsonNode candidateWords, List<ClozeBlankResponse> blanks) {
        return of(quiz, wordbookId, candidateWords, blanks, null);
    }

    public static ClozeQuizResponse of(
            ClozeQuiz quiz,
            Long wordbookId,
            JsonNode candidateWords,
            List<ClozeBlankResponse> blanks,
            ClozeAttemptResponse attempt
    ) {
        return new ClozeQuizResponse(
                String.valueOf(quiz.getId()),
                wordbookId == null ? null : String.valueOf(wordbookId),
                quiz.getTitle(),
                quiz.getPassage(),
                quiz.getExplanation(),
                candidateWords,
                blanks,
                attempt
        );
    }
}
