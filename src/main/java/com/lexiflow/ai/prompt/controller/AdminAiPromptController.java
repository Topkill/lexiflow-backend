package com.lexiflow.ai.prompt.controller;

import com.lexiflow.ai.prompt.domain.AiPromptFeatureType;
import com.lexiflow.ai.prompt.dto.AiPromptFeatureBindingRequest;
import com.lexiflow.ai.prompt.dto.AiPromptFeatureGroupResponse;
import com.lexiflow.ai.prompt.dto.AiPromptTemplateRequest;
import com.lexiflow.ai.prompt.dto.AiPromptTemplateResponse;
import com.lexiflow.ai.prompt.service.AiPromptTemplateService;
import com.lexiflow.auth.security.AuthContext;
import com.lexiflow.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员 AI 提示词控制器
 * <p>
 * 提供 AI 提示词模板的管理接口，包括模板的查询、创建、编辑、复制、删除以及功能绑定操作。
 * </p>
 */
@Tag(name = "管理员 AI 提示词接口")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/ai")
public class AdminAiPromptController {

    private final AiPromptTemplateService aiPromptTemplateService;
    /**
     * 查询 AI 提示词模板数据。
     */

    @Operation(summary = "AI 提示词模板分组列表")
    @GetMapping("/prompt-templates")
    public ApiResponse<List<AiPromptFeatureGroupResponse>> listPromptTemplates(
            @RequestParam(required = false, defaultValue = "0") Long wordbookId
    ) {
        return ApiResponse.success(aiPromptTemplateService.listGroups(wordbookId));
    }

    @Operation(summary = "新增 AI 提示词模板")
    @PostMapping("/prompt-templates")
    public ApiResponse<AiPromptTemplateResponse> createPromptTemplate(@Valid @RequestBody AiPromptTemplateRequest request) {
        return ApiResponse.success(aiPromptTemplateService.createTemplate(AuthContext.currentUserId(), request));
    }

    @Operation(summary = "编辑 AI 提示词模板")
    @PutMapping("/prompt-templates/{templateId}")
    public ApiResponse<AiPromptTemplateResponse> updatePromptTemplate(
            @PathVariable @Positive Long templateId,
            @Valid @RequestBody AiPromptTemplateRequest request
    ) {
        return ApiResponse.success(aiPromptTemplateService.updateTemplate(AuthContext.currentUserId(), templateId, request));
    }

    @Operation(summary = "复制自定义 AI 提示词模板")
    @PostMapping("/prompt-templates/{templateId}/copy")
    public ApiResponse<AiPromptTemplateResponse> copyPromptTemplate(
            @PathVariable @Positive Long templateId,
            @RequestParam(required = false) Long wordbookId
    ) {
        return ApiResponse.success(aiPromptTemplateService.copyTemplate(AuthContext.currentUserId(), templateId, wordbookId));
    }

    @Operation(summary = "复制内置 AI 提示词模板")
    @PostMapping("/prompt-templates/builtin/{featureType}/copy")
    public ApiResponse<AiPromptTemplateResponse> copyBuiltinPromptTemplate(
            @PathVariable AiPromptFeatureType featureType,
            @RequestParam(required = false, defaultValue = "0") Long wordbookId
    ) {
        return ApiResponse.success(aiPromptTemplateService.copyBuiltin(AuthContext.currentUserId(), featureType, wordbookId));
    }

    @Operation(summary = "删除自定义 AI 提示词模板")
    @DeleteMapping("/prompt-templates/{templateId}")
    public ApiResponse<Void> deletePromptTemplate(@PathVariable @Positive Long templateId) {
        aiPromptTemplateService.deleteTemplate(templateId);
        return ApiResponse.success();
    }

    @Operation(summary = "设置 AI 功能当前提示词模板")
    @PutMapping("/prompt-features/{featureType}/binding")
    public ApiResponse<Void> bindPromptTemplate(
            @PathVariable AiPromptFeatureType featureType,
            @RequestBody(required = false) AiPromptFeatureBindingRequest request
    ) {
        aiPromptTemplateService.bindFeature(AuthContext.currentUserId(), featureType, request);
        return ApiResponse.success();
    }
}
