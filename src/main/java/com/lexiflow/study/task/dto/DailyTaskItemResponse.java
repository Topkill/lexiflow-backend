package com.lexiflow.study.task.dto;

import com.lexiflow.study.task.domain.DailyTaskItem;
import com.lexiflow.wordbook.domain.Word;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "今日任务明细响应")
public record DailyTaskItemResponse(
        @Schema(description = "任务项 ID", example = "1900000000000005001") String itemId,
        @Schema(description = "单词 ID", example = "1900000000000002001") String wordId,
        @Schema(description = "任务项类型", example = "NEW") String itemType,
        @Schema(description = "任务项状态", example = "PENDING") String status,
        @Schema(description = "规范单词", example = "ability") String wordText,
        @Schema(description = "展示单词", example = "ability") String displayText,
        @Schema(description = "美式音标", example = "əˈbɪləti") String phoneticUs,
        @Schema(description = "主释义", example = "能力；才能") String primaryDefinition
) {
    public static DailyTaskItemResponse from(DailyTaskItem item, Word word) {
        return new DailyTaskItemResponse(
                String.valueOf(item.getId()),
                String.valueOf(item.getWordId()),
                item.getItemType().name(),
                item.getStatus().name(),
                word.getWordText(),
                word.getDisplayText(),
                word.getPhoneticUs(),
                word.getPrimaryDefinition()
        );
    }
}
