package com.lexiflow.ai.content.controller;

import com.lexiflow.ai.content.dto.WordAiQuestionRequest;
import com.lexiflow.ai.content.dto.WordAiContentResponse;
import com.lexiflow.ai.content.service.WordAiContentService;
import com.lexiflow.auth.security.AuthContext;
import com.lexiflow.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@Tag(name = "AI 单词问答接口")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/ai/words")
public class WordAiContentController {

    private final WordAiContentService wordAiContentService;

    @Operation(summary = "AI 单词问答")
    @PostMapping("/{wordId}/questions")
    public ApiResponse<WordAiContentResponse> askQuestion(
            @PathVariable @Positive Long wordId,
            @Valid @RequestBody WordAiQuestionRequest request
    ) {
        return ApiResponse.success(wordAiContentService.generateWordQuestion(
                AuthContext.currentUserId(),
                request.wordbookId(),
                wordId,
                request.safeQuestion(),
                request.shouldRegenerate()
        ));
    }

    @Operation(summary = "创建 AI 单词问答任务")
    @PostMapping("/{wordId}/question-tasks")
    public ApiResponse<WordAiContentResponse> createQuestionTask(
            @PathVariable @Positive Long wordId,
            @Valid @RequestBody WordAiQuestionRequest request
    ) {
        return ApiResponse.success(wordAiContentService.createWordQuestionTask(
                AuthContext.currentUserId(),
                request.wordbookId(),
                wordId,
                request.safeQuestion(),
                request.shouldRegenerate()
        ));
    }

    @Operation(summary = "查询 AI 单词问答状态")
    @GetMapping("/{wordId}/questions/state")
    public ApiResponse<WordAiContentResponse> getQuestionState(
            @PathVariable @Positive Long wordId,
            @RequestParam @Positive Long wordbookId,
            @RequestParam String question
    ) {
        return ApiResponse.success(wordAiContentService.getWordQuestionState(
                AuthContext.currentUserId(),
                wordbookId,
                wordId,
                question
        ));
    }

    @Operation(summary = "流式 AI 单词问答")
    @PostMapping(value = "/{wordId}/questions/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> streamQuestion(
            @PathVariable @Positive Long wordId,
            @Valid @RequestBody WordAiQuestionRequest request
    ) {
        Long userId = AuthContext.currentUserId();
        StreamingResponseBody body = outputStream -> wordAiContentService.streamWordQuestion(
                userId,
                request.wordbookId(),
                wordId,
                request.safeQuestion(),
                request.shouldRegenerate(),
                outputStream
        );
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .header(HttpHeaders.CONNECTION, "keep-alive")
                .header("X-Accel-Buffering", "no")
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(body);
    }
}
