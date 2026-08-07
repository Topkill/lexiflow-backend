package com.lexiflow.wordbook.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * 词库单词查询请求 DTO。
 * <p>用于分页查询词库内的单词，支持按单词或释义关键字筛选。</p>
 *
 * @param page 当前页码，默认为1
 * @param size 每页数量，默认为20，最大100
 * @param keyword 单词或释义关键字
 */
@Schema(description = "词库单词查询参数")
public record WordQueryRequest(
        @Schema(description = "当前页码", example = "1") @Min(1) Long page,
        @Schema(description = "每页数量", example = "20") @Min(1) @Max(100) Long size,
        @Schema(description = "单词或释义关键字", example = "ability") @Size(max = 64) String keyword
) {
    public WordQueryRequest {
        page = page == null ? 1L : page;
        size = size == null ? 20L : size;
    }
}
