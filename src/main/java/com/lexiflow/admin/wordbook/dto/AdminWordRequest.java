package com.lexiflow.admin.wordbook.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "后台单词保存请求")
public record AdminWordRequest(
        @Schema(description = "英文单词") @NotBlank @Size(max = 128) String wordText,
        @Schema(description = "展示单词") @Size(max = 128) String displayText,
        @Schema(description = "美式音标") @Size(max = 128) String phoneticUs,
        @Schema(description = "英式音标") @Size(max = 128) String phoneticUk,
        @Schema(description = "释义 JSON 或文本") @NotBlank String meanings,
        @Schema(description = "主要词性") @Size(max = 32) String primaryPos,
        @Schema(description = "主释义") @Size(max = 512) String primaryDefinition,
        @Schema(description = "英文例句") @Size(max = 1024) String exampleSentence,
        @Schema(description = "例句翻译") @Size(max = 1024) String exampleTranslation,
        @Schema(description = "标签") @Size(max = 512) String tags,
        @Schema(description = "词库内顺序") @NotNull @Min(1) Integer sequenceNo,
        @Schema(description = "词库内难度") @NotNull @Min(1) @Max(5) Integer difficultyLevel,
        @Schema(description = "考频或权重") @NotNull @Min(0) Integer examFrequency,
        @Schema(description = "是否启用") @NotNull Boolean enabled
) {
}