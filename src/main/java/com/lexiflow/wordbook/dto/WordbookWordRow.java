package com.lexiflow.wordbook.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "词库单词查询行")
public record WordbookWordRow(
        Long id,
        String wordText,
        String displayText,
        String phoneticUs,
        String phoneticUk,
        String primaryPos,
        String primaryDefinition,
        String exampleSentence,
        String exampleTranslation,
        String tags,
        Integer sequenceNo,
        Integer difficultyLevel,
        Integer examFrequency
) {
}
