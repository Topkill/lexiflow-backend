package com.lexiflow.admin.controller;

import com.lexiflow.admin.dto.AdminAiCallLogQueryRequest;
import com.lexiflow.admin.dto.AdminAiCallLogResponse;
import com.lexiflow.admin.service.AdminAiCallLogService;
import com.lexiflow.common.api.ApiResponse;
import com.lexiflow.common.api.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台 AI 调用日志接口控制器。
 *
 * <p>提供管理员查询 AI 调用日志的分页接口。</p>
 */
@Tag(name = "后台 AI 调用日志接口")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/ai/call-logs")
public class AdminAiCallLogController {

    private final AdminAiCallLogService adminAiCallLogService;
    /**
     * 管理端-AI调用日志分页查询。
     */

    @Operation(summary = "AI 调用日志分页")
    @GetMapping
    public ApiResponse<PageResponse<AdminAiCallLogResponse>> pageLogs(@Valid AdminAiCallLogQueryRequest request) {
        return ApiResponse.success(adminAiCallLogService.pageLogs(request));
    }
}
