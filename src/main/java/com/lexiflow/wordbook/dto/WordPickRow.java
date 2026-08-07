package com.lexiflow.wordbook.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 词库新词候选行 DTO。
 * <p>用于学习模块中挑选新词，包含单词ID和排序序号。</p>
 *
 * @param wordId 单词ID
 * @param sequenceNo 排序序号
 */
@Schema(description = "词库新词候选行")
public record WordPickRow(
        Long wordId,
        Integer sequenceNo
) {
}
