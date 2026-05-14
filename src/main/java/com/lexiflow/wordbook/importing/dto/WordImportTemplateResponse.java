package com.lexiflow.wordbook.importing.dto;

public record WordImportTemplateResponse(
        String fileName,
        byte[] content
) {
}