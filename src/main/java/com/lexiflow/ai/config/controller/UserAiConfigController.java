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

    /**
     * 查询当前用户的私有 AI 配置信息
     * <p>
     * 该方法通过认证上下文获取当前登录用户的ID，并查询该用户配置的AI相关参数，
     * 包括AI模型选择、API密钥等配置信息。
     * </p>
     *
     * @return ApiResponse 统一响应对象，包含用户的AI配置信息
     *         - UserAiConfigResponse: 用户AI配置响应对象，包含完整的AI配置详情
     */
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
