package com.lexiflow.study.task.dto;

import com.lexiflow.study.progress.domain.StudyFeedback;
import com.lexiflow.study.task.domain.DailyTaskItem;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

@Schema(description = "提交反馈响应")
public record SubmitFeedbackResponse(
        @Schema(description = "任务项 ID", example = "1900000000000005001") String itemId,
        @Schema(description = "任务项状态", example = "DONE") String status,
        @Schema(description = "反馈", example = "KNOWN") String feedback,
        @Schema(description = "下次复习日期", example = "2026-05-17") LocalDate nextReviewDate,
        @Schema(description = "今日任务是否完成", example = "false") Boolean dailyTaskDone,
        @Schema(description = "任务进度") TaskProgressResponse taskProgress
) {
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
