package com.lexiflow.study.controller;

import com.lexiflow.auth.security.AuthContext;
import com.lexiflow.common.api.ApiResponse;
import com.lexiflow.study.dto.CreateStudyPlanRequest;
import com.lexiflow.study.dto.StudyPlanResponse;
import com.lexiflow.study.dto.UpdateStudyPlanRequest;
import com.lexiflow.study.service.StudyPlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "学习计划接口")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/study/plans")
public class StudyPlanController {

    private final StudyPlanService studyPlanService;

    @Operation(summary = "创建学习计划")
    @PostMapping
    public ApiResponse<StudyPlanResponse> create(@Valid @RequestBody CreateStudyPlanRequest request) {
        return ApiResponse.success(studyPlanService.createPlan(AuthContext.currentUserId(), request));
    }

    @Operation(summary = "查询主学习计划")
    @GetMapping("/primary")
    public ApiResponse<StudyPlanResponse> primary() {
        return ApiResponse.success(studyPlanService.getPrimaryPlan(AuthContext.currentUserId()));
    }

    @Operation(summary = "更新学习计划")
    @PutMapping("/{planId}")
    public ApiResponse<StudyPlanResponse> update(
            @PathVariable @Positive Long planId,
            @Valid @RequestBody UpdateStudyPlanRequest request
    ) {
        return ApiResponse.success(studyPlanService.updatePlan(AuthContext.currentUserId(), planId, request));
    }

    @Operation(summary = "暂停学习计划")
    @PostMapping("/{planId}/pause")
    public ApiResponse<StudyPlanResponse> pause(@PathVariable @Positive Long planId) {
        return ApiResponse.success(studyPlanService.pausePlan(AuthContext.currentUserId(), planId));
    }

    @Operation(summary = "恢复学习计划")
    @PostMapping("/{planId}/resume")
    public ApiResponse<StudyPlanResponse> resume(@PathVariable @Positive Long planId) {
        return ApiResponse.success(studyPlanService.resumePlan(AuthContext.currentUserId(), planId));
    }

    @Operation(summary = "结束学习计划")
    @PostMapping("/{planId}/end")
    public ApiResponse<StudyPlanResponse> end(@PathVariable @Positive Long planId) {
        return ApiResponse.success(studyPlanService.endPlan(AuthContext.currentUserId(), planId));
    }
}
