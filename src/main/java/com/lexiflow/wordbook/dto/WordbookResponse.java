package com.lexiflow.wordbook.dto;

import com.lexiflow.wordbook.domain.Wordbook;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 词库响应 DTO。
 * <p>返回词库的基本信息和当前用户的学习进度。</p>
 *
 * @param id 词库ID
 * @param name 词库名称
 * @param code 词库编码
 * @param type 词库类型
 * @param description 词库描述
 * @param coverUrl 封面图片URL
 * @param difficultyLevel 难度等级
 * @param wordCount 单词数量
 * @param enabled 是否启用
 * @param progress 当前用户学习进度
 */
@Schema(description = "词库响应")
public record WordbookResponse(
        @Schema(description = "词库 ID", example = "1900000000000001001") String id,
        @Schema(description = "词库名称", example = "CET4 核心词") String name,
        @Schema(description = "词库编码", example = "CET4_CORE") String code,
        @Schema(description = "词库类型", example = "CET4") String type,
        @Schema(description = "词库描述") String description,
        @Schema(description = "封面图") String coverUrl,
        @Schema(description = "难度等级", example = "2") Integer difficultyLevel,
        @Schema(description = "单词数量", example = "4500") Integer wordCount,
        @Schema(description = "是否启用", example = "true") Boolean enabled,
        @Schema(description = "当前用户进度，未接入学习模块前为空") WordbookProgressResponse progress
) {
    public static WordbookResponse from(Wordbook wordbook, WordbookProgressResponse progress) {
        return new WordbookResponse(
                String.valueOf(wordbook.getId()),
                wordbook.getName(),
                wordbook.getCode(),
                wordbook.getType().name(),
                wordbook.getDescription(),
                wordbook.getCoverUrl(),
                wordbook.getDifficultyLevel(),
                wordbook.getWordCount(),
                wordbook.getEnabled(),
                progress
        );
    }
}
