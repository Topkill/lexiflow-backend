package com.lexiflow.wordbook.importing.dto;

import com.lexiflow.wordbook.importing.domain.WordImportError;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "单词导入错误响应")
public record WordImportErrorResponse(
        @Schema(description = "错误 ID") String id,
        @Schema(description = "行号") Integer rowNo,
        @Schema(description = "单词") String wordText,
        @Schema(description = "错误码") String errorCode,
        @Schema(description = "错误说明") String errorMessage,
        @Schema(description = "原始数据") String rawData
) {
    public static WordImportErrorResponse from(WordImportError error) {
        return new WordImportErrorResponse(
                String.valueOf(error.getId()),
                error.getRowNo(),
                error.getWordText(),
                error.getErrorCode(),
                error.getErrorMessage(),
                error.getRawData()
        );
    }
}