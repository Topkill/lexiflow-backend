package com.lexiflow.study.task.controller;

import com.lexiflow.auth.security.AuthContext;
import com.lexiflow.common.api.ApiResponse;
import com.lexiflow.study.task.dto.DailyTaskResponse;
import com.lexiflow.study.task.service.DailyTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "今日任务接口")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/study/tasks")
public class DailyTaskController {

    private final DailyTaskService dailyTaskService;

    @Operation(summary = "查询今日任务")
    @GetMapping("/today")
    public ApiResponse<DailyTaskResponse> today() {
        return ApiResponse.success(dailyTaskService.getTodayTask(AuthContext.currentUserId()));
    }
}
