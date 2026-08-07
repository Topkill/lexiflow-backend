package com.lexiflow.study.task.controller;

import com.lexiflow.auth.security.AuthContext;
import com.lexiflow.common.api.ApiResponse;
import com.lexiflow.study.task.dto.CreateWrongWordPracticeRequest;
import com.lexiflow.study.task.dto.DailyTaskResponse;
import com.lexiflow.study.task.dto.SubmitFeedbackRequest;
import com.lexiflow.study.task.dto.SubmitFeedbackResponse;
import com.lexiflow.study.task.dto.TaskItemCardResponse;
import com.lexiflow.study.task.service.DailyTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

/**
 * 今日任务控制器。
 * <p>提供每日任务查询、错词练习、学习卡片和反馈提交等接口。</p>
 */
@Tag(name = "今日任务接口")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/study")
public class DailyTaskController {

    private final DailyTaskService dailyTaskService;

    /** 查询当前用户的今日学习任务。 */
    @Operation(summary = "查询今日任务")
    @GetMapping("/tasks/today")
    public ApiResponse<DailyTaskResponse> today() {
        return ApiResponse.success(dailyTaskService.getTodayTask(AuthContext.currentUserId()));
    }

    /** 按 ID 查询指定学习任务的详情。 */
    @Operation(summary = "按 ID 查询学习任务")
    @GetMapping("/tasks/{taskId}")
    public ApiResponse<DailyTaskResponse> task(@PathVariable @Positive Long taskId) {
        return ApiResponse.success(dailyTaskService.getTask(AuthContext.currentUserId(), taskId));
    }

    /** 创建错词专项复习任务。 */
    @Operation(summary = "创建错词专项复习")
    @PostMapping("/tasks/today/wrong-word-practice")
    public ApiResponse<DailyTaskResponse> createWrongWordPractice(@Valid @RequestBody CreateWrongWordPracticeRequest request) {
        return ApiResponse.success(dailyTaskService.createWrongWordPractice(AuthContext.currentUserId(), request));
    }

    /** 获取学习卡片详情，包含单词完整信息和选择题。 */
    @Operation(summary = "获取学习卡片详情")
    @GetMapping("/task-items/{itemId}/card")
    public ApiResponse<TaskItemCardResponse> card(@PathVariable @Positive Long itemId) {
        return ApiResponse.success(dailyTaskService.getCard(AuthContext.currentUserId(), itemId));
    }

    /** 提交单词学习反馈（认识/不认识）。 */
    @Operation(summary = "提交单词反馈")
    @PostMapping("/task-items/{itemId}/feedback")
    public ApiResponse<SubmitFeedbackResponse> feedback(
            @PathVariable @Positive Long itemId,
            @Valid @RequestBody SubmitFeedbackRequest request
    ) {
        return ApiResponse.success(dailyTaskService.submitFeedback(AuthContext.currentUserId(), itemId, request));
    }
}
