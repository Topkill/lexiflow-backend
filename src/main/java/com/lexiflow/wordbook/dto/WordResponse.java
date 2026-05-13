package com.lexiflow.wordbook.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "单词响应")
public record WordResponse(
        @Schema(description = "单词 ID", example = "1900000000000002001") String id,
        @Schema(description = "规范单词", example = "ability") String wordText,
        @Schema(description = "展示单词", example = "ability") String displayText,
        @Schema(description = "美式音标", example = "/əˈbɪləti/") String phoneticUs,
        @Schema(description = "英式音标", example = "/əˈbɪləti/") String phoneticUk,
        @Schema(description = "主要词性", example = "n.") String primaryPos,
        @Schema(description = "主释义", example = "能力；才能") String primaryDefinition,
        @Schema(description = "默认英文例句") String exampleSentence,
        @Schema(description = "默认例句翻译") String exampleTranslation,
        @Schema(description = "标签") String tags,
        @Schema(description = "词库内顺序", example = "1") Integer sequenceNo,
        @Schema(description = "词库内难度", example = "2") Integer difficultyLevel,
        @Schema(description = "考频或权重", example = "95") Integer examFrequency
) {
    public static WordResponse from(WordbookWordRow row) {
        return new WordResponse(
                String.valueOf(row.id()),
                row.wordText(),
                row.displayText(),
                row.phoneticUs(),
                row.phoneticUk(),
                row.primaryPos(),
                row.primaryDefinition(),
                row.exampleSentence(),
                row.exampleTranslation(),
                row.tags(),
                row.sequenceNo(),
                row.difficultyLevel(),
                row.examFrequency()
        );
    }
}
