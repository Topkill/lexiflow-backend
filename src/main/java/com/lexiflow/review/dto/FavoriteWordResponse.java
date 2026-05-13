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
        @Schema(description = "展示单词") String displayText,
        @Schema(description = "美式音标") String phoneticUs,
        @Schema(description = "主释义") String primaryDefinition,
        @Schema(description = "备注") String note,
        @Schema(description = "收藏时间") LocalDateTime createdAt
) {
    public static FavoriteWordResponse from(FavoriteWord favoriteWord, Word word) {
        return new FavoriteWordResponse(
                String.valueOf(favoriteWord.getId()),
                String.valueOf(favoriteWord.getWordbookId()),
                String.valueOf(favoriteWord.getWordId()),
                word.getDisplayText(),
                word.getPhoneticUs(),
                word.getPrimaryDefinition(),
                favoriteWord.getNote(),
                favoriteWord.getCreatedAt()
        );
    }
}
