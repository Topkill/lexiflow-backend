package com.lexiflow.wordbook.importing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Schema(description = "单词导入错误查询")
public record WordImportErrorQueryRequest(
        @Schema(description = "页码") @Min(1) Long page,
        @Schema(description = "每页数量") @Min(1) @Max(200) Long size
) {
    public WordImportErrorQueryRequest {
        page = page == null ? 1L : page;
        size = size == null ? 50L : size;
    }
}
