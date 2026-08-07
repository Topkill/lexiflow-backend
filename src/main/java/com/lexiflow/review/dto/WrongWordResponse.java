package com.lexiflow.review.dto;

import com.lexiflow.study.progress.domain.WrongWord;
import com.lexiflow.wordbook.domain.Word;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/**
 * 错词响应。
 *
 * @param wrongWordId  错词记录 ID
 * @param wordbookId   词库 ID
 * @param wordId       单词 ID
 * @param word         单词文本
 * @param phonetic0    英式音标
 * @param phonetic1    美式音标
 * @param primaryDefinition 主释义
 * @param wrongCount   错误次数
 * @param lastSource   最近来源
 * @param lastWrongAt  最近错误时间
 * @param resolved     是否已解决
 */
@Schema(description = "错词响应")
public record WrongWordResponse(
        @Schema(description = "错词记录 ID") String wrongWordId,
        @Schema(description = "词库 ID") String wordbookId,
        @Schema(description = "单词 ID") String wordId,
        @Schema(description = "单词") String word,
        @Schema(description = "英式音标") String phonetic0,
        @Schema(description = "美式音标") String phonetic1,
        @Schema(description = "主释义") String primaryDefinition,
        @Schema(description = "错误次数") Integer wrongCount,
        @Schema(description = "最近来源") String lastSource,
        @Schema(description = "最近错误时间") LocalDateTime lastWrongAt,
        @Schema(description = "是否已解决") Boolean resolved
) {
    /**
     * 从错词实体和单词实体构造响应。
     *
     * @param wrongWord 错词实体
     * @param word      单词实体
     * @return 响应实例
     */
    public static WrongWordResponse from(WrongWord wrongWord, Word word) {
        return new WrongWordResponse(
                String.valueOf(wrongWord.getId()),
                String.valueOf(wrongWord.getWordbookId()),
                String.valueOf(wrongWord.getWordId()),
                word.getWord(),
                word.getPhonetic0(),
                word.getPhonetic1(),
                word.getPrimaryDefinition(),
                wrongWord.getWrongCount(),
                wrongWord.getLastSource().name(),
                wrongWord.getLastWrongAt(),
                wrongWord.getResolved()
        );
    }
}
