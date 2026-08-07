package com.lexiflow.wordbook.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 词库学习进度响应 DTO。
 * <p>返回用户在指定词库中的学习进度统计信息。</p>
 *
 * @param learnedCount 已学习词数
 * @param masteredCount 已掌握词数
 * @param progressRate 完成百分比
 */
@Schema(description = "词库学习进度，学习模块接入后返回真实数据")
public record WordbookProgressResponse(
        @Schema(description = "已学习词数", example = "120") Integer learnedCount,
        @Schema(description = "已掌握词数", example = "80") Integer masteredCount,
        @Schema(description = "完成百分比", example = "24.5") Double progressRate
) {
}
