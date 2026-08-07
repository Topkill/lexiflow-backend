package com.lexiflow.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/**
 * 后台数据看板概览响应 DTO。
 *
 * <p>包含系统核心业务指标的统计数据，包括用户、词库、单词、AI 调用等。</p>
 *
 * @param registeredUsers 注册用户总数
 * @param activeUsers     活跃用户数
 * @param todayLearners   今日学习人数
 * @param wordbookCount   词库数量
 * @param wordCount       单词数量
 * @param aiCallCount     AI 调用总次数
 * @param aiSuccessRate   AI 调用成功率（百分比）
 * @param todayAiCallCount 今日 AI 调用次数
 */
@Schema(description = "后台数据看板概览响应")
public record AdminOverviewResponse(
        @Schema(description = "注册用户数") Long registeredUsers,
        @Schema(description = "活跃用户数") Long activeUsers,
        @Schema(description = "今日学习人数") Long todayLearners,
        @Schema(description = "词库数量") Long wordbookCount,
        @Schema(description = "单词数量") Long wordCount,
        @Schema(description = "AI 调用次数") Long aiCallCount,
        @Schema(description = "AI 调用成功率") BigDecimal aiSuccessRate,
        @Schema(description = "今日 AI 调用次数") Long todayAiCallCount
) {
}