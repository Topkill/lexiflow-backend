package com.lexiflow.study.task.dto;

import com.lexiflow.study.progress.domain.StudyFeedback;
import com.lexiflow.study.task.domain.DailyTaskItem;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

/**
 * 提交反馈响应 DTO。
 * <p>包含反馈后的任务项状态、下次复习日期和任务进度信息。</p>
 */
@Schema(description = "提交反馈响应")
public record SubmitFeedbackResponse(
        @Schema(description = "任务项 ID", example = "1900000000000005001") String itemId,
        @Schema(description = "任务项状态", example = "DONE") String status,
        @Schema(description = "反馈", example = "KNOWN") String feedback,
        @Schema(description = "下次复习日期", example = "2026-05-17") LocalDate nextReviewDate,
        @Schema(description = "今日任务是否完成", example = "false") Boolean dailyTaskDone,
        @Schema(description = "任务进度") TaskProgressResponse taskProgress
) {
    /** 从实体对象构建响应。 */
    public static SubmitFeedbackResponse from(
            DailyTaskItem item,
            StudyFeedback feedback,
            LocalDate nextReviewDate,
            boolean dailyTaskDone,
            TaskProgressResponse taskProgress
    ) {
        return new SubmitFeedbackResponse(
                String.valueOf(item.getId()),
                item.getStatus().name(),
                feedback.name(),
                nextReviewDate,
                dailyTaskDone,
                taskProgress
        );
    }
}
