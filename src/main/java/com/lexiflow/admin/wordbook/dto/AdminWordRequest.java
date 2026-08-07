package com.lexiflow.admin.wordbook.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 后台单词保存请求 DTO。
 *
 * <p>用于创建或更新单词，包含单词的详细信息、字典数据及作用域配置。</p>
 */
@Schema(description = "后台单词保存请求")
public record AdminWordRequest(
        @Schema(description = "单词展示值") @NotBlank @Size(max = 128) String word,
        @Schema(description = "英式音标 phonetic0") @Size(max = 128) String phonetic0,
        @Schema(description = "美式音标 phonetic1") @Size(max = 128) String phonetic1,
        @Schema(description = "释义 JSON") @NotBlank String trans,
        @Schema(description = "例句 JSON") String sentences,
        @Schema(description = "短语 JSON") String phrases,
        @Schema(description = "同近义词 JSON") String synos,
        @Schema(description = "相关词 JSON") String relWords,
        @Schema(description = "词源 JSON") String etymology,
        @Schema(description = "主要词性") @Size(max = 32) String primaryPos,
        @Schema(description = "主释义") @Size(max = 512) String primaryDefinition,
        @Schema(description = "标签") @Size(max = 512) String tags,
        @Schema(description = "词库内顺序") @NotNull @Min(1) Integer sequenceNo,
        @Schema(description = "词库内难度") @NotNull @Min(1) @Max(5) Integer difficultyLevel,
        @Schema(description = "考频或权重") @NotNull @Min(0) Integer examFrequency,
        @Schema(description = "是否启用") @NotNull Boolean enabled
) {
}
