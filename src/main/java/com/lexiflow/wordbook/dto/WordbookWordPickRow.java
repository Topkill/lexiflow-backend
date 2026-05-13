package com.lexiflow.wordbook.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "词库新词候选行")
public record WordbookWordPickRow(
        Long wordId,
        Integer sequenceNo
) {
}
