package com.lexiflow.wordbook.dto;

public record WordbookWordAdminRow(
        Long id,
        Long relationId,
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
        Integer examFrequency,
        Boolean enabled
) {
}