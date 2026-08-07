package com.lexiflow.review.dto;

import com.lexiflow.study.progress.domain.UserWordState;
import com.lexiflow.wordbook.domain.Word;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

/**
 * 到期复习词响应。
 *
 * @param stateId           单词状态 ID
 * @param wordbookId        词库 ID
 * @param wordId            单词 ID
 * @param word              单词文本
 * @param phonetic0         英式音标
 * @param phonetic1         美式音标
 * @param primaryDefinition 主释义
 * @param masteryStatus     掌握状态
 * @param nextReviewDate    下次复习日期
 */
@Schema(description = "到期复习词响应")
public record ReviewWordResponse(
        @Schema(description = "单词状态 ID") String stateId,
        @Schema(description = "词库 ID") String wordbookId,
        @Schema(description = "单词 ID") String wordId,
        @Schema(description = "单词") String word,
        @Schema(description = "英式音标") String phonetic0,
        @Schema(description = "美式音标") String phonetic1,
        @Schema(description = "主释义") String primaryDefinition,
        @Schema(description = "掌握状态") String masteryStatus,
        @Schema(description = "下次复习日期") LocalDate nextReviewDate
) {
    /**
     * 从用户单词状态和单词实体构造响应。
     *
     * @param state 用户单词状态
     * @param word  单词实体
     * @return 响应实例
     */
    public static ReviewWordResponse from(UserWordState state, Word word) {
        return new ReviewWordResponse(
                String.valueOf(state.getId()),
                String.valueOf(state.getWordbookId()),
                String.valueOf(state.getWordId()),
                word.getWord(),
                word.getPhonetic0(),
                word.getPhonetic1(),
                word.getPrimaryDefinition(),
                state.getMasteryStatus().name(),
                state.getNextReviewDate()
        );
    }
}
