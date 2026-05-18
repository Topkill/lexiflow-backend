package com.lexiflow.wordbook.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "词库内单词查询行")
public record WordRow(
        Long id,
        String word,
        String normalizedWord,
        String phonetic0,
        String phonetic1,
        String trans,
        String sentences,
        String phrases,
        String synos,
        String relWords,
        String etymology,
        String primaryPos,
        String primaryDefinition,
        String tags,
        Integer sequenceNo,
        Integer difficultyLevel,
        Integer examFrequency
) {
}
