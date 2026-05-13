package com.lexiflow.user.controller;

import com.lexiflow.auth.security.AuthContext;
import com.lexiflow.common.api.ApiResponse;
import com.lexiflow.user.dto.ChangePasswordRequest;
import com.lexiflow.user.dto.UpdateProfileRequest;
import com.lexiflow.user.dto.UpdateUserSettingsRequest;
import com.lexiflow.user.dto.UserProfileResponse;
import com.lexiflow.user.dto.UserSettingsResponse;
import com.lexiflow.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "用户接口")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users/me")
public class UserController {

    private final UserService userService;

    @Operation(summary = "查询个人资料")
    @GetMapping("/profile")
    public ApiResponse<UserProfileResponse> profile() {
        return ApiResponse.success(UserProfileResponse.from(userService.getActiveUserById(AuthContext.currentUserId())));
    }

    @Operation(summary = "更新个人资料")
    @PutMapping("/profile")
    public ApiResponse<UserProfileResponse> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.success(UserProfileResponse.from(userService.updateProfile(AuthContext.currentUserId(), request)));
    }

    @Operation(summary = "修改密码")
    @PutMapping("/password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(AuthContext.currentUserId(), request);
        return ApiResponse.success();
    }

    @Operation(summary = "查询用户设置")
    @GetMapping("/settings")
    public ApiResponse<UserSettingsResponse> settings() {
        return ApiResponse.success(UserSettingsResponse.from(userService.getOrCreateSettings(AuthContext.currentUserId())));
    }

    @Operation(summary = "更新用户设置")
    @PutMapping("/settings")
    public ApiResponse<UserSettingsResponse> updateSettings(@Valid @RequestBody UpdateUserSettingsRequest request) {
        return ApiResponse.success(UserSettingsResponse.from(userService.updateSettings(AuthContext.currentUserId(), request)));
    }
}
