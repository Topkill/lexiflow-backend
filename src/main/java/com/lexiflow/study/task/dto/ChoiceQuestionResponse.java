package com.lexiflow.study.task.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 回忆阶段中文释义选择题 DTO。
 * <p>包含正确选项下标和选项列表，用于学习卡片中的选择题展示。</p>
 */
@Schema(description = "回忆阶段中文释义选择题")
public record ChoiceQuestionResponse(
        @Schema(description = "正确选项下标", example = "2") Integer correctIndex,
        @Schema(description = "选项列表") List<ChoiceQuestionOptionResponse> options
) {
}
