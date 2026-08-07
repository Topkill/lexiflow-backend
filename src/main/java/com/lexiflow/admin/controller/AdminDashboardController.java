package com.lexiflow.admin.controller;

import com.lexiflow.admin.dto.AdminOverviewResponse;
import com.lexiflow.admin.service.AdminDashboardService;
import com.lexiflow.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台看板接口控制器。
 *
 * <p>提供管理员数据看板概览接口，展示系统整体统计数据。</p>
 */
@Tag(name = "后台看板接口")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/dashboard")
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;
    /**
     * 管理端-数据看板。
     */

    @Operation(summary = "数据看板概览")
    @GetMapping("/overview")
    public ApiResponse<AdminOverviewResponse> overview() {
        return ApiResponse.success(adminDashboardService.overview());
    }
}
