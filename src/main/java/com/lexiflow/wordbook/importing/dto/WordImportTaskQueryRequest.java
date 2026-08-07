package com.lexiflow.wordbook.importing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 单词导入任务查询请求 DTO。
 * <p>用于分页查询导入任务列表，支持按词库ID筛选。</p>
 *
 * @param page 页码，默认为1
 * @param size 每页数量，默认为5，最大100
 * @param wordbookId 词库ID
 */
@Schema(description = "单词导入任务查询")
public record WordImportTaskQueryRequest(
        @Schema(description = "页码") @Min(1) Long page,
        @Schema(description = "每页数量") @Min(1) @Max(100) Long size,
        @Schema(description = "词库 ID") @Min(1) Long wordbookId
) {
    public WordImportTaskQueryRequest {
        page = page == null ? 1L : page;
        size = size == null ? 5L : size;
    }
}
