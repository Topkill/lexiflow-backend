package com.lexiflow.admin.wordbook.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "后台词库单词响应")
public record AdminWordResponse(
        @Schema(description = "单词 ID") String id,
        @Schema(description = "词库关联 ID") String relationId,
        @Schema(description = "规范单词") String wordText,
        @Schema(description = "展示单词") String displayText,
        @Schema(description = "美式音标") String phoneticUs,
        @Schema(description = "英式音标") String phoneticUk,
        @Schema(description = "主要词性") String primaryPos,
        @Schema(description = "主释义") String primaryDefinition,
        @Schema(description = "例句") String exampleSentence,
        @Schema(description = "例句翻译") String exampleTranslation,
        @Schema(description = "标签") String tags,
        @Schema(description = "顺序") Integer sequenceNo,
        @Schema(description = "难度") Integer difficultyLevel,
        @Schema(description = "考频") Integer examFrequency,
        @Schema(description = "是否启用") Boolean enabled
) {
}