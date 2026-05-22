package com.lexiflow.wordbook.dto;

public record LexiflowDictionaryEntryRow(
        Long id,
        String word,
        String normalizedWord,
        String phoneticsJson,
        String primaryPos,
        String primaryDefinition,
        String sensesJson,
        String source
) {
}
