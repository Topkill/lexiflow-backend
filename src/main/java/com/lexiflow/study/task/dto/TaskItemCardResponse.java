package com.lexiflow.study.task.dto;

import com.lexiflow.study.progress.domain.MasteryStatus;
import com.lexiflow.wordbook.domain.Word;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "学习卡片详情响应")
public record TaskItemCardResponse(
        @Schema(description = "任务项 ID", example = "1900000000000005001") String itemId,
        @Schema(description = "词库 ID", example = "1900000000000001001") String wordbookId,
        @Schema(description = "单词 ID", example = "1900000000000002001") String wordId,
        @Schema(description = "任务项类型", example = "NEW") String itemType,
        @Schema(description = "任务项状态", example = "PENDING") String status,
        @Schema(description = "单词", example = "ability") String word,
        @Schema(description = "规范化单词", example = "ability") String normalizedWord,
        @Schema(description = "英式音标") String phonetic0,
        @Schema(description = "美式音标") String phonetic1,
        @Schema(description = "释义 JSON") String trans,
        @Schema(description = "例句 JSON") String sentences,
        @Schema(description = "短语 JSON") String phrases,
        @Schema(description = "同近义词 JSON") String synos,
        @Schema(description = "相关词 JSON") String relWords,
        @Schema(description = "词源 JSON") String etymology,
        @Schema(description = "主要词性", example = "n.") String primaryPos,
        @Schema(description = "主释义", example = "能力；才能") String primaryDefinition,
        @Schema(description = "标签") String tags,
        @Schema(description = "收藏 ID，未收藏时为空") String favoriteWordId,
        @Schema(description = "是否已收藏", example = "false") Boolean favorite,
        @Schema(description = "掌握状态", example = "NEW") String masteryStatus
) {
    public static TaskItemCardResponse from(
            com.lexiflow.study.task.domain.DailyTaskItem item,
            Word word,
            Long favoriteWordId,
            MasteryStatus masteryStatus
    ) {
        return new TaskItemCardResponse(
                String.valueOf(item.getId()),
                String.valueOf(item.getWordbookId()),
                String.valueOf(item.getWordId()),
                item.getItemType().name(),
                item.getStatus().name(),
                word.getWord(),
                word.getNormalizedWord(),
                word.getPhonetic0(),
                word.getPhonetic1(),
                word.getTrans(),
                word.getSentences(),
                word.getPhrases(),
                word.getSynos(),
                word.getRelWords(),
                word.getEtymology(),
                word.getPrimaryPos(),
                word.getPrimaryDefinition(),
                word.getTags(),
                favoriteWordId == null ? null : String.valueOf(favoriteWordId),
                favoriteWordId != null,
                masteryStatus.name()
        );
    }
}
