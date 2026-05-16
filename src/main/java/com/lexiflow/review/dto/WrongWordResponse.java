package com.lexiflow.review.dto;

import com.lexiflow.study.progress.domain.WrongWord;
import com.lexiflow.wordbook.domain.Word;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

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
