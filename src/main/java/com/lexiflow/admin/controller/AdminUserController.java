package com.lexiflow.admin.controller;

import com.lexiflow.admin.dto.AdminUserDetailResponse;
import com.lexiflow.admin.dto.AdminUserQueryRequest;
import com.lexiflow.admin.dto.AdminUserResponse;
import com.lexiflow.admin.service.AdminUserService;
import com.lexiflow.common.api.ApiResponse;
import com.lexiflow.common.api.PageResponse;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "后台用户管理接口")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;
    /**
     * 管理端-用户管理。
     */

    @Operation(summary = "用户分页列表")
    @GetMapping
    public ApiResponse<PageResponse<AdminUserResponse>> pageUsers(@Valid @ModelAttribute AdminUserQueryRequest request) {
        return ApiResponse.success(adminUserService.pageUsers(request));
    }

    @Operation(summary = "用户详情")
    @GetMapping("/{userId}")
    public ApiResponse<AdminUserDetailResponse> getUser(@PathVariable @Positive Long userId) {
        return ApiResponse.success(adminUserService.getUser(userId));
    }

    @Operation(summary = "禁用用户")
    @PostMapping("/{userId}/disable")
    public ApiResponse<Void> disableUser(@PathVariable @Positive Long userId) {
        adminUserService.disableUser(userId);
        return ApiResponse.success();
    }

    @Operation(summary = "启用用户")
    @PostMapping("/{userId}/enable")
    public ApiResponse<Void> enableUser(@PathVariable @Positive Long userId) {
        adminUserService.enableUser(userId);
        return ApiResponse.success();
    }
}
