package com.lexiflow.wordbook.importing.controller;

import com.lexiflow.auth.security.AuthContext;
import com.lexiflow.common.api.ApiResponse;
import com.lexiflow.common.api.PageResponse;
import com.lexiflow.wordbook.importing.domain.WordImportDuplicateStrategy;
import com.lexiflow.wordbook.importing.dto.WordImportErrorQueryRequest;
import com.lexiflow.wordbook.importing.dto.WordImportErrorResponse;
import com.lexiflow.wordbook.importing.dto.WordImportJsonUrlRequest;
import com.lexiflow.wordbook.importing.dto.WordImportTaskQueryRequest;
import com.lexiflow.wordbook.importing.dto.WordImportTaskResponse;
import com.lexiflow.wordbook.importing.dto.WordImportTemplateResponse;
import com.lexiflow.wordbook.importing.service.WordImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 后台单词导入控制器。
 * <p>提供 Excel 模板下载、Excel 文件上传导入、JSON URL 导入、导入任务查询、错误报告下载等功能。</p>
 *
 * @see WordImportService
 */
@Tag(name = "后台 Excel 单词导入接口")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin")
public class AdminWordImportController {

    private final WordImportService wordImportService;
    /**
     * 执行单词导入。
     */

    @Operation(summary = "下载单词导入模板")
    @GetMapping("/imports/word-template")
    public void downloadTemplate(HttpServletResponse response) throws java.io.IOException {
        WordImportTemplateResponse template = wordImportService.buildTemplate();
        writeWorkbook(response, template.fileName(), template.content());
    }

    @Operation(summary = "上传 Excel 并导入单词")
    @PostMapping(value = "/wordbooks/{wordbookId}/imports", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<WordImportTaskResponse> importWords(
            @PathVariable @Positive Long wordbookId,
            @RequestParam(defaultValue = "SKIP") WordImportDuplicateStrategy duplicateStrategy,
            @RequestParam("file") MultipartFile file
    ) {
        return ApiResponse.success(wordImportService.importWords(AuthContext.currentUserId(), wordbookId, duplicateStrategy, file));
    }

    @Operation(summary = "通过远程 JSON URL 导入单词")
    @PostMapping("/wordbooks/{wordbookId}/imports/json-url")
    public ApiResponse<WordImportTaskResponse> importWordsFromJsonUrl(
            @PathVariable @Positive Long wordbookId,
            @Valid @RequestBody WordImportJsonUrlRequest request
    ) {
        return ApiResponse.success(wordImportService.importWordsFromJsonUrl(AuthContext.currentUserId(), wordbookId, request));
    }

    @Operation(summary = "分页查询导入任务")
    @GetMapping("/imports")
    public ApiResponse<PageResponse<WordImportTaskResponse>> pageTasks(@Valid @ModelAttribute WordImportTaskQueryRequest request) {
        return ApiResponse.success(wordImportService.pageTasks(request));
    }

    @Operation(summary = "查询导入任务详情")
    @GetMapping("/imports/{importTaskId}")
    public ApiResponse<WordImportTaskResponse> getTask(@PathVariable @Positive Long importTaskId) {
        return ApiResponse.success(wordImportService.getTask(importTaskId));
    }

    @Operation(summary = "查询导入错误明细")
    @GetMapping("/imports/{importTaskId}/errors")
    public ApiResponse<PageResponse<WordImportErrorResponse>> pageErrors(
            @PathVariable @Positive Long importTaskId,
            @Valid @ModelAttribute WordImportErrorQueryRequest request
    ) {
        return ApiResponse.success(wordImportService.pageErrors(importTaskId, request));
    }

    @Operation(summary = "下载导入错误报告")
    @GetMapping("/imports/{importTaskId}/error-report")
    public void downloadErrorReport(@PathVariable @Positive Long importTaskId, HttpServletResponse response) throws java.io.IOException {
        byte[] content = wordImportService.buildErrorReport(importTaskId);
        writeWorkbook(response, "lexiflow-word-import-errors-" + importTaskId + ".xlsx", content);
    }

    private void writeWorkbook(HttpServletResponse response, String fileName, byte[] content) throws java.io.IOException {
        String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFileName);
        response.setContentLength(content.length);
        response.getOutputStream().write(content);
    }
}
