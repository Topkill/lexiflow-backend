package com.lexiflow.ai.config.controller;

import com.lexiflow.ai.config.dto.AiPublicConfigRequest;
import com.lexiflow.ai.config.dto.AiPublicConfigResponse;
import com.lexiflow.ai.config.service.AiPublicConfigService;
import com.lexiflow.auth.security.AuthContext;
import com.lexiflow.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "管理员 AI 配置接口")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/ai/public-configs")
public class AdminAiConfigController {

    private final AiPublicConfigService aiPublicConfigService;
    /**
     * 查询AI公共配置列表。
     */

    @Operation(summary = "公共配置列表")
    @GetMapping
    public ApiResponse<List<AiPublicConfigResponse>> listConfigs() {
        return ApiResponse.success(aiPublicConfigService.listConfigs());
    }

    @Operation(summary = "新增公共配置")
    @PostMapping
    public ApiResponse<AiPublicConfigResponse> createConfig(@Valid @RequestBody AiPublicConfigRequest request) {
        return ApiResponse.success(aiPublicConfigService.createConfig(AuthContext.currentUserId(), request));
    }

    @Operation(summary = "编辑公共配置")
    @PutMapping("/{configId}")
    public ApiResponse<AiPublicConfigResponse> updateConfig(
            @PathVariable @Positive Long configId,
            @Valid @RequestBody AiPublicConfigRequest request
    ) {
        return ApiResponse.success(aiPublicConfigService.updateConfig(AuthContext.currentUserId(), configId, request));
    }

    @Operation(summary = "激活公共配置")
    @PostMapping("/{configId}/activate")
    public ApiResponse<Void> activateConfig(@PathVariable @Positive Long configId) {
        aiPublicConfigService.activateConfig(AuthContext.currentUserId(), configId);
        return ApiResponse.success();
    }

    @Operation(summary = "启用公共配置")
    @PostMapping("/{configId}/enable")
    public ApiResponse<Void> enableConfig(@PathVariable @Positive Long configId) {
        aiPublicConfigService.enableConfig(AuthContext.currentUserId(), configId);
        return ApiResponse.success();
    }

    @Operation(summary = "停用公共配置")
    @PostMapping("/{configId}/disable")
    public ApiResponse<Void> disableConfig(@PathVariable @Positive Long configId) {
        aiPublicConfigService.disableConfig(AuthContext.currentUserId(), configId);
        return ApiResponse.success();
    }
}
