package com.lexiflow.study.task.dto;

import com.lexiflow.study.progress.domain.StudyFeedback;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 提交单词反馈请求 DTO。
 * <p>用户在学习卡片上提交“认识”或“不认识”的反馈。</p>
 */
@Schema(description = "提交单词反馈请求")
public record SubmitFeedbackRequest(
        @Schema(description = "反馈", example = "KNOWN")
        @NotNull
        StudyFeedback feedback,

        @Schema(description = "学习耗时秒数", example = "8")
        @Min(0)
        @Max(3600)
        Integer durationSeconds
) {
}
