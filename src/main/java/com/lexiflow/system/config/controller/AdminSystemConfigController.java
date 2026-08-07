package com.lexiflow.system.config.controller;

import com.lexiflow.auth.security.AuthContext;
import com.lexiflow.common.api.ApiResponse;
import com.lexiflow.common.api.PageResponse;
import com.lexiflow.system.config.dto.SystemConfigQueryRequest;
import com.lexiflow.system.config.dto.SystemConfigRequest;
import com.lexiflow.system.config.dto.SystemConfigResponse;
import com.lexiflow.system.config.service.SystemConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台系统配置控制器。
 * <p>提供系统配置的增删改查接口，仅管理员可访问。</p>
 */
@Tag(name = "后台系统配置接口")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/system-configs")
public class AdminSystemConfigController {

    private final SystemConfigService systemConfigService;

    /** 分页查询系统配置列表，支持按类型、可编辑状态和关键词过滤。 */
    @Operation(summary = "系统配置分页列表")
    @GetMapping
    public ApiResponse<PageResponse<SystemConfigResponse>> pageConfigs(@Valid @ModelAttribute SystemConfigQueryRequest request) {
        return ApiResponse.success(systemConfigService.pageConfigs(request));
    }

    /** 根据 ID 查询单条系统配置详情。 */
    @Operation(summary = "系统配置详情")
    @GetMapping("/{configId}")
    public ApiResponse<SystemConfigResponse> getConfig(@PathVariable @Positive Long configId) {
        return ApiResponse.success(systemConfigService.getConfig(configId));
    }

    /** 新增一条系统配置。 */
    @Operation(summary = "新增系统配置")
    @PostMapping
    public ApiResponse<SystemConfigResponse> createConfig(@Valid @RequestBody SystemConfigRequest request) {
        return ApiResponse.success(systemConfigService.createConfig(AuthContext.currentUserId(), request));
    }

    /** 编辑已有的系统配置，仅可编辑标记为 editable 的配置。 */
    @Operation(summary = "编辑系统配置")
    @PutMapping("/{configId}")
    public ApiResponse<SystemConfigResponse> updateConfig(
            @PathVariable @Positive Long configId,
            @Valid @RequestBody SystemConfigRequest request
    ) {
        return ApiResponse.success(systemConfigService.updateConfig(AuthContext.currentUserId(), configId, request));
    }

    /** 删除一条系统配置，仅可删除标记为 editable 的配置。 */
    @Operation(summary = "删除系统配置")
    @DeleteMapping("/{configId}")
    public ApiResponse<Void> deleteConfig(@PathVariable @Positive Long configId) {
        systemConfigService.deleteConfig(configId);
        return ApiResponse.success();
    }
}
