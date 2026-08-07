package com.lexiflow.wordbook.importing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 单词导入错误查询请求 DTO。
 * <p>用于分页查询导入错误记录。</p>
 *
 * @param page 页码，默认为1
 * @param size 每页数量，默认为50，最大200
 */
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
