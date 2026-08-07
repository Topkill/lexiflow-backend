package com.lexiflow.admin.dto;

import com.lexiflow.ai.content.domain.AiCallStatus;
import com.lexiflow.ai.content.domain.AiContentType;
import com.lexiflow.ai.core.dto.AiConfigScope;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;

/**
 * 后台 AI 调用日志查询请求 DTO。
 *
 * <p>支持按用户 ID、配置范围、内容类型、状态、时间范围等条件筛选。</p>
 *
 * @param page        页码，默认 1
 * @param size        每页数量，默认 20
 * @param userId      用户 ID（可选）
 * @param configScope 配置范围（可选）
 * @param contentType 内容类型（可选）
 * @param status      调用状态（可选）
 * @param startDate   开始日期（可选）
 * @param endDate     结束日期（可选）
 */
@Schema(description = "后台 AI 调用日志查询")
public record AdminAiCallLogQueryRequest(
        @Schema(description = "页码") @Min(1) Long page,
        @Schema(description = "每页数量") @Min(1) @Max(100) Long size,
        @Schema(description = "用户 ID") Long userId,
        @Schema(description = "配置范围") AiConfigScope configScope,
        @Schema(description = "内容类型") AiContentType contentType,
        @Schema(description = "状态") AiCallStatus status,
        @Schema(description = "开始日期") LocalDate startDate,
        @Schema(description = "结束日期") LocalDate endDate
) {
    public AdminAiCallLogQueryRequest {
        page = page == null ? 1L : page;
        size = size == null ? 20L : size;
    }
}
