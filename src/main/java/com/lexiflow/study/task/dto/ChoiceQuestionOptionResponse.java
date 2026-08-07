package com.lexiflow.study.task.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 选择题选项 DTO。
 * <p>表示回忆阶段中文释义选择题的一个选项。</p>
 */
@Schema(description = "选择题选项")
public record ChoiceQuestionOptionResponse(
        @Schema(description = "单词 ID", example = "1900000000000002001") String wordId,
        @Schema(description = "词性", example = "n.") String pos,
        @Schema(description = "中文释义", example = "能力；才能") String definition
) {
}
