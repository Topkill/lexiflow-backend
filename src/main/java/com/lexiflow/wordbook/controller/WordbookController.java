package com.lexiflow.wordbook.controller;

import com.lexiflow.common.api.ApiResponse;
import com.lexiflow.common.api.PageResponse;
import com.lexiflow.wordbook.dto.WordQueryRequest;
import com.lexiflow.wordbook.dto.WordResponse;
import com.lexiflow.wordbook.dto.WordbookQueryRequest;
import com.lexiflow.wordbook.dto.WordbookResponse;
import com.lexiflow.wordbook.service.WordbookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "词库接口")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/wordbooks")
public class WordbookController {

    private final WordbookService wordbookService;

    @Operation(summary = "词库列表")
    @GetMapping
    public ApiResponse<List<WordbookResponse>> list(@Valid WordbookQueryRequest request) {
        return ApiResponse.success(wordbookService.listWordbooks(request));
    }

    @Operation(summary = "词库详情")
    @GetMapping("/{wordbookId}")
    public ApiResponse<WordbookResponse> detail(@PathVariable @Positive Long wordbookId) {
        return ApiResponse.success(wordbookService.getWordbook(wordbookId));
    }

    @Operation(summary = "词库单词列表")
    @GetMapping("/{wordbookId}/words")
    public ApiResponse<PageResponse<WordResponse>> words(
            @PathVariable @Positive Long wordbookId,
            @Valid WordQueryRequest request
    ) {
        return ApiResponse.success(wordbookService.pageWords(wordbookId, request));
    }
}
