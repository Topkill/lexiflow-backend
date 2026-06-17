package com.lexiflow.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

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