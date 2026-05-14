package com.lexiflow.study.statistics.controller;

import com.lexiflow.auth.security.AuthContext;
import com.lexiflow.common.api.ApiResponse;
import com.lexiflow.study.statistics.dto.StudyStatisticsOverviewResponse;
import com.lexiflow.study.statistics.service.StudyStatisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "学习统计接口")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/study/statistics")
public class StudyStatisticsController {

    private final StudyStatisticsService studyStatisticsService;

    @Operation(summary = "学习统计概览")
    @GetMapping("/overview")
    public ApiResponse<StudyStatisticsOverviewResponse> overview() {
        return ApiResponse.success(studyStatisticsService.overview(AuthContext.currentUserId()));
    }
}