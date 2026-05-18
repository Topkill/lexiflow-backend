package com.lexiflow.admin.wordbook.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "后台词库单词响应")
public record AdminWordResponse(
        @Schema(description = "单词 ID") String id,
        @Schema(description = "单词展示值") String word,
        @Schema(description = "规范化单词") String normalizedWord,
        @Schema(description = "英式音标 phonetic0") String phonetic0,
        @Schema(description = "美式音标 phonetic1") String phonetic1,
        @Schema(description = "释义 JSON") String trans,
        @Schema(description = "例句 JSON") String sentences,
        @Schema(description = "短语 JSON") String phrases,
        @Schema(description = "同近义词 JSON") String synos,
        @Schema(description = "相关词 JSON") String relWords,
        @Schema(description = "词源 JSON") String etymology,
        @Schema(description = "主要词性") String primaryPos,
        @Schema(description = "主释义") String primaryDefinition,
        @Schema(description = "标签") String tags,
        @Schema(description = "顺序") Integer sequenceNo,
        @Schema(description = "难度") Integer difficultyLevel,
        @Schema(description = "考频") Integer examFrequency,
        @Schema(description = "是否启用") Boolean enabled
) {
}
