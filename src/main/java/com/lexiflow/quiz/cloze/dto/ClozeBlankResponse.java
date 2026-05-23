package com.lexiflow.quiz.cloze.dto;

import com.lexiflow.quiz.cloze.domain.ClozeQuizBlank;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "完形填空空格响应")
public record ClozeBlankResponse(
        @Schema(description = "单词 ID") String wordId,
        @Schema(description = "空格 ID") String blankId,
        @Schema(description = "空格序号") Integer blankNo,
        @Schema(description = "提示") String hint
) {
    public static ClozeBlankResponse from(ClozeQuizBlank blank) {
        return new ClozeBlankResponse(blank.getWordId() == null ? null : String.valueOf(blank.getWordId()), String.valueOf(blank.getId()), blank.getBlankNo(), blank.getHint());
    }
}
