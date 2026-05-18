package com.lexiflow.wordbook.dto;

public record AdminWordRow(
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
        Integer examFrequency,
        Boolean enabled
) {
}
