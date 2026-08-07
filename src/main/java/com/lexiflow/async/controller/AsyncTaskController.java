package com.lexiflow.async.controller;

import com.lexiflow.async.dto.AsyncTaskResponse;
import com.lexiflow.async.service.AsyncTaskService;
import com.lexiflow.auth.security.AuthContext;
import com.lexiflow.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 异步任务控制器
 * <p>
 * 提供异步任务状态查询的 REST 接口，客户端可通过任务 ID 查询任务执行进度和结果。
 * </p>
 */
@Tag(name = "异步任务接口")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/tasks")
public class AsyncTaskController {

    private final AsyncTaskService asyncTaskService;

    @Operation(summary = "查询任务状态")
    @GetMapping("/{taskId}")
    public ApiResponse<AsyncTaskResponse> getTask(@PathVariable @Positive Long taskId) {
        return ApiResponse.success(asyncTaskService.getTask(AuthContext.currentUserId(), taskId));
    }
}