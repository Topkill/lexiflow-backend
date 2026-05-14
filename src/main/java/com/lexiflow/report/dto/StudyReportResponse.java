package com.lexiflow.report.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.lexiflow.report.domain.StudyReport;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "学习报告响应")
public record StudyReportResponse(
        @Schema(description = "报告 ID") String id,
        @Schema(description = "报告日期") LocalDate reportDate,
        @Schema(description = "新词数") Integer newWordsCount,
        @Schema(description = "复习词数") Integer reviewWordsCount,
        @Schema(description = "测验正确率") BigDecimal quizAccuracy,
        @Schema(description = "结构化总结") JsonNode summary,
        @Schema(description = "Markdown 内容") String markdownContent
) {
    public static StudyReportResponse of(StudyReport report, JsonNode summary) {
        return new StudyReportResponse(
                String.valueOf(report.getId()),
                report.getReportDate(),
                report.getNewWordsCount(),
                report.getReviewWordsCount(),
                report.getQuizAccuracy(),
                summary,
                report.getMarkdownContent()
        );
    }
}