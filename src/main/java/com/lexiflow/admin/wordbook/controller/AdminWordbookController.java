package com.lexiflow.admin.wordbook.controller;

import com.lexiflow.admin.wordbook.dto.AdminWordQueryRequest;
import com.lexiflow.admin.wordbook.dto.AdminWordRequest;
import com.lexiflow.admin.wordbook.dto.AdminWordResponse;
import com.lexiflow.admin.wordbook.dto.AdminWordbookQueryRequest;
import com.lexiflow.admin.wordbook.dto.AdminWordbookRequest;
import com.lexiflow.admin.wordbook.dto.AdminWordbookResponse;
import com.lexiflow.admin.wordbook.service.AdminWordbookService;
import com.lexiflow.auth.security.AuthContext;
import com.lexiflow.common.api.ApiResponse;
import com.lexiflow.common.api.PageResponse;
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
 * 后台词库与单词管理接口控制器。
 *
 * <p>提供管理员对词库和单词的 CRUD 操作，包括分页查询、详情查看、新增、编辑、启用/停用等功能。</p>
 */
@Tag(name = "后台词库与单词管理接口")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/wordbooks")
public class AdminWordbookController {

    private final AdminWordbookService adminWordbookService;
    /**
     * 分页查询词库列表。
     *
     * @param request 词库查询请求参数，包含分页信息及筛选条件
     * @return 包含分页词库数据的统一响应结果
     */
    @Operation(summary = "词库分页列表")
    @GetMapping
    public ApiResponse<PageResponse<AdminWordbookResponse>> pageWordbooks(@Valid @ModelAttribute AdminWordbookQueryRequest request) {
        return ApiResponse.success(adminWordbookService.pageWordbooks(request));
    }

    @Operation(summary = "词库详情")
    @GetMapping("/{wordbookId}")
    public ApiResponse<AdminWordbookResponse> getWordbook(@PathVariable @Positive Long wordbookId) {
        return ApiResponse.success(adminWordbookService.getWordbook(wordbookId));
    }

    @Operation(summary = "新增词库")
    @PostMapping
    public ApiResponse<AdminWordbookResponse> createWordbook(@Valid @RequestBody AdminWordbookRequest request) {
        return ApiResponse.success(adminWordbookService.createWordbook(AuthContext.currentUserId(), request));
    }

    @Operation(summary = "编辑词库")
    @PutMapping("/{wordbookId}")
    public ApiResponse<AdminWordbookResponse> updateWordbook(
            @PathVariable @Positive Long wordbookId,
            @Valid @RequestBody AdminWordbookRequest request
    ) {
        return ApiResponse.success(adminWordbookService.updateWordbook(AuthContext.currentUserId(), wordbookId, request));
    }

    @Operation(summary = "启用词库")
    @PostMapping("/{wordbookId}/enable")
    public ApiResponse<Void> enableWordbook(@PathVariable @Positive Long wordbookId) {
        adminWordbookService.enableWordbook(AuthContext.currentUserId(), wordbookId);
        return ApiResponse.success();
    }

    @Operation(summary = "停用词库")
    @PostMapping("/{wordbookId}/disable")
    public ApiResponse<Void> disableWordbook(@PathVariable @Positive Long wordbookId) {
        adminWordbookService.disableWordbook(AuthContext.currentUserId(), wordbookId);
        return ApiResponse.success();
    }

    @Operation(summary = "词库单词分页")
    @GetMapping("/{wordbookId}/words")
    public ApiResponse<PageResponse<AdminWordResponse>> pageWords(
            @PathVariable @Positive Long wordbookId,
            @Valid @ModelAttribute AdminWordQueryRequest request
    ) {
        return ApiResponse.success(adminWordbookService.pageWords(wordbookId, request));
    }

    @Operation(summary = "新增词库单词")
    @PostMapping("/{wordbookId}/words")
    public ApiResponse<AdminWordResponse> createWord(
            @PathVariable @Positive Long wordbookId,
            @Valid @RequestBody AdminWordRequest request
    ) {
        return ApiResponse.success(adminWordbookService.createWord(AuthContext.currentUserId(), wordbookId, request));
    }

    @Operation(summary = "编辑词库单词")
    @PutMapping("/{wordbookId}/words/{wordId}")
    public ApiResponse<AdminWordResponse> updateWord(
            @PathVariable @Positive Long wordbookId,
            @PathVariable @Positive Long wordId,
            @Valid @RequestBody AdminWordRequest request
    ) {
        return ApiResponse.success(adminWordbookService.updateWord(AuthContext.currentUserId(), wordbookId, wordId, request));
    }

    @Operation(summary = "启用词库单词")
    @PostMapping("/{wordbookId}/words/{wordId}/enable")
    public ApiResponse<Void> enableWord(
            @PathVariable @Positive Long wordbookId,
            @PathVariable @Positive Long wordId
    ) {
        adminWordbookService.enableWord(AuthContext.currentUserId(), wordbookId, wordId);
        return ApiResponse.success();
    }

    @Operation(summary = "停用词库单词")
    @PostMapping("/{wordbookId}/words/{wordId}/disable")
    public ApiResponse<Void> disableWord(
            @PathVariable @Positive Long wordbookId,
            @PathVariable @Positive Long wordId
    ) {
        adminWordbookService.disableWord(AuthContext.currentUserId(), wordbookId, wordId);
        return ApiResponse.success();
    }

    @Operation(summary = "删除词库单词")
    @DeleteMapping("/{wordbookId}/words/{wordId}")
    public ApiResponse<Void> removeWord(
            @PathVariable @Positive Long wordbookId,
            @PathVariable @Positive Long wordId
    ) {
        adminWordbookService.removeWord(wordbookId, wordId);
        return ApiResponse.success();
    }
}
