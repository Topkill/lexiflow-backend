package com.lexiflow.quiz.cloze.dto;

import com.lexiflow.quiz.cloze.domain.ClozeQuizBlank;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 完形填空空格响应 DTO。
 * <p>返回完形填空中每个空的信息。</p>
 *
 * @param wordId 单词ID
 * @param blankId 空格ID
 * @param blankNo 空格序号
 * @param hint 提示
 * @param hintPos 词性提示
 * @param hintDefinition 中文释义提示
 */
@Schema(description = "完形填空空格响应")
public record ClozeBlankResponse(
        @Schema(description = "单词 ID") String wordId,
        @Schema(description = "空格 ID") String blankId,
        @Schema(description = "空格序号") Integer blankNo,
        @Schema(description = "提示") String hint,
        @Schema(description = "词性提示") String hintPos,
        @Schema(description = "中文释义提示") String hintDefinition
) {
    public static ClozeBlankResponse from(ClozeQuizBlank blank) {
        return from(blank, null, null);
    }

    public static ClozeBlankResponse from(ClozeQuizBlank blank, String hintPos, String hintDefinition) {
        return new ClozeBlankResponse(
                blank.getWordId() == null ? null : String.valueOf(blank.getWordId()),
                String.valueOf(blank.getId()),
                blank.getBlankNo(),
                blank.getHint(),
                hintPos,
                hintDefinition
        );
    }
}
