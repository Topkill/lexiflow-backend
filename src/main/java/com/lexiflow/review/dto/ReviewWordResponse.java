package com.lexiflow.review.dto;

import com.lexiflow.study.progress.domain.UserWordState;
import com.lexiflow.wordbook.domain.Word;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

@Schema(description = "到期复习词响应")
public record ReviewWordResponse(
        @Schema(description = "单词状态 ID") String stateId,
        @Schema(description = "词库 ID") String wordbookId,
        @Schema(description = "单词 ID") String wordId,
        @Schema(description = "展示单词") String displayText,
        @Schema(description = "美式音标") String phoneticUs,
        @Schema(description = "主释义") String primaryDefinition,
        @Schema(description = "掌握状态") String masteryStatus,
        @Schema(description = "下次复习日期") LocalDate nextReviewDate
) {
    public static ReviewWordResponse from(UserWordState state, Word word) {
        return new ReviewWordResponse(
                String.valueOf(state.getId()),
                String.valueOf(state.getWordbookId()),
                String.valueOf(state.getWordId()),
                word.getDisplayText(),
                word.getPhoneticUs(),
                word.getPrimaryDefinition(),
                state.getMasteryStatus().name(),
                state.getNextReviewDate()
        );
    }
}
