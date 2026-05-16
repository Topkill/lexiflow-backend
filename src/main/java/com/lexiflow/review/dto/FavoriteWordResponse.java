package com.lexiflow.review.dto;

import com.lexiflow.study.progress.domain.FavoriteWord;
import com.lexiflow.wordbook.domain.Word;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "收藏词响应")
public record FavoriteWordResponse(
        @Schema(description = "收藏 ID") String favoriteWordId,
        @Schema(description = "词库 ID") String wordbookId,
        @Schema(description = "单词 ID") String wordId,
        @Schema(description = "单词") String word,
        @Schema(description = "英式音标") String phonetic0,
        @Schema(description = "美式音标") String phonetic1,
        @Schema(description = "主释义") String primaryDefinition,
        @Schema(description = "备注") String note,
        @Schema(description = "收藏时间") LocalDateTime createdAt
) {
    public static FavoriteWordResponse from(FavoriteWord favoriteWord, Word word) {
        return new FavoriteWordResponse(
                String.valueOf(favoriteWord.getId()),
                String.valueOf(favoriteWord.getWordbookId()),
                String.valueOf(favoriteWord.getWordId()),
                word.getWord(),
                word.getPhonetic0(),
                word.getPhonetic1(),
                word.getPrimaryDefinition(),
                favoriteWord.getNote(),
                favoriteWord.getCreatedAt()
        );
    }
}
