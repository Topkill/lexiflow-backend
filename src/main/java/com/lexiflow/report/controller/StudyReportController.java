package com.lexiflow.report.controller;

import com.lexiflow.auth.security.AuthContext;
import com.lexiflow.common.api.ApiResponse;
import com.lexiflow.common.api.PageResponse;
import com.lexiflow.report.dto.CreateReportTaskRequest;
import com.lexiflow.report.dto.CreateReportTaskResponse;
import com.lexiflow.report.dto.ReportQueryRequest;
import com.lexiflow.report.dto.StudyReportResponse;
import com.lexiflow.report.service.StudyReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 学习报告接口控制器。
 * <p>提供创建报告任务、查询单个报告和分页查询报告列表的 REST 端点。</p>
 */
@Tag(name = "AI 学习报告接口")
@Validated
@RestController
@RequiredArgsConstructor
public class StudyReportController {

    private final StudyReportService studyReportService;

    /**
     * 创建 AI 学习报告生成任务。
     *
     * @param request 包含每日任务 ID 和报告日期
     * @return 包含异步任务状态的响应
     */
    @Operation(summary = "创建 AI 学习报告任务")
    @PostMapping("/api/v1/ai/report-tasks")
    public ApiResponse<CreateReportTaskResponse> createReportTask(@Valid @RequestBody CreateReportTaskRequest request) {
        return ApiResponse.success(studyReportService.createReportTask(AuthContext.currentUserId(), request));
    }

    /**
     * 查询指定学习报告详情。
     *
     * @param reportId 报告 ID
     * @return 报告响应
     */
    @Operation(summary = "查询学习报告")
    @GetMapping("/api/v1/reports/{reportId}")
    public ApiResponse<StudyReportResponse> getReport(@PathVariable @Positive Long reportId) {
        return ApiResponse.success(studyReportService.getReport(AuthContext.currentUserId(), reportId));
    }

    /**
     * 分页查询当前用户的学习报告列表。
     *
     * @param request 分页查询参数，包含页码、每页数量和日期范围
     * @return 分页报告响应
     */
    @Operation(summary = "查询我的学习报告列表")
    @GetMapping("/api/v1/reports")
    public ApiResponse<PageResponse<StudyReportResponse>> pageReports(@Valid @ModelAttribute ReportQueryRequest request) {
        return ApiResponse.success(studyReportService.pageReports(AuthContext.currentUserId(), request));
    }
}