package com.lexiflow.study.statistics.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/**
 * 学习统计概览响应 DTO。
 * <p>汇总用户的学习数据，包括词数统计、连续学习天数、任务完成率、测验正确率等。</p>
 */
@Schema(description = "学习统计概览响应")
public record StudyStatisticsOverviewResponse(
        @Schema(description = "累计学习词数") Long learnedWords,
        @Schema(description = "已掌握词数") Long masteredWords,
        @Schema(description = "待复习词数") Long dueReviewWords,
        @Schema(description = "困难词数量") Long difficultWords,
        @Schema(description = "连续学习天数") Integer streakDays,
        @Schema(description = "今日任务完成率") BigDecimal todayTaskCompletionRate,
        @Schema(description = "完形填空正确率") BigDecimal clozeAccuracy,
        @Schema(description = "当前词库完成进度") BigDecimal currentWordbookProgress,
        @Schema(description = "当前主计划 ID") String primaryPlanId,
        @Schema(description = "当前词库 ID") String wordbookId
) {
}