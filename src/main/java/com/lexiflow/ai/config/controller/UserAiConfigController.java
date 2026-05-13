package com.lexiflow.ai.config.controller;

import com.lexiflow.ai.config.dto.SaveUserAiConfigRequest;
import com.lexiflow.ai.config.dto.UserAiConfigResponse;
import com.lexiflow.ai.config.service.UserAiConfigService;
import com.lexiflow.auth.security.AuthContext;
import com.lexiflow.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "用户私有 AI 配置接口")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users/me/ai-config")
public class UserAiConfigController {

    private final UserAiConfigService userAiConfigService;

    @Operation(summary = "查询私有 AI 配置")
    @GetMapping
    public ApiResponse<UserAiConfigResponse> getConfig() {
        return ApiResponse.success(userAiConfigService.getConfig(AuthContext.currentUserId()));
    }

    @Operation(summary = "保存私有 AI 配置")
    @PutMapping
    public ApiResponse<UserAiConfigResponse> saveConfig(@Valid @RequestBody SaveUserAiConfigRequest request) {
        return ApiResponse.success(userAiConfigService.saveConfig(AuthContext.currentUserId(), request));
    }
}
