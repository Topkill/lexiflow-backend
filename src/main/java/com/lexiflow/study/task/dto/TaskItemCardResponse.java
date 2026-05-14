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
        @Schema(description = "规范单词", example = "ability") String wordText,
        @Schema(description = "展示单词", example = "ability") String displayText,
        @Schema(description = "美式音标") String phoneticUs,
        @Schema(description = "英式音标") String phoneticUk,
        @Schema(description = "主要词性", example = "n.") String primaryPos,
        @Schema(description = "主释义", example = "能力；才能") String primaryDefinition,
        @Schema(description = "默认英文例句") String exampleSentence,
        @Schema(description = "默认例句翻译") String exampleTranslation,
        @Schema(description = "标签") String tags,
        @Schema(description = "是否已收藏", example = "false") Boolean favorite,
        @Schema(description = "掌握状态", example = "NEW") String masteryStatus
) {
    public static TaskItemCardResponse from(
            com.lexiflow.study.task.domain.DailyTaskItem item,
            Word word,
            boolean favorite,
            MasteryStatus masteryStatus
    ) {
        return new TaskItemCardResponse(
                String.valueOf(item.getId()),
                String.valueOf(item.getWordbookId()),
                String.valueOf(item.getWordId()),
                item.getItemType().name(),
                item.getStatus().name(),
                word.getWordText(),
                word.getDisplayText(),
                word.getPhoneticUs(),
                word.getPhoneticUk(),
                word.getPrimaryPos(),
                word.getPrimaryDefinition(),
                word.getExampleSentence(),
                word.getExampleTranslation(),
                word.getTags(),
                favorite,
                masteryStatus.name()
        );
    }
}
