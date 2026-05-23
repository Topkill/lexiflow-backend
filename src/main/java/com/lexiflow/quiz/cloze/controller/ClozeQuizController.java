package com.lexiflow.quiz.cloze.controller;

import com.lexiflow.auth.security.AuthContext;
import com.lexiflow.common.api.ApiResponse;
import com.lexiflow.quiz.cloze.dto.ClozeAttemptResponse;
import com.lexiflow.quiz.cloze.dto.ClozeAttemptAiReviewResponse;
import com.lexiflow.quiz.cloze.dto.ClozeQuizResponse;
import com.lexiflow.quiz.cloze.dto.CreateClozeTaskRequest;
import com.lexiflow.quiz.cloze.dto.CreateClozeTaskResponse;
import com.lexiflow.quiz.cloze.dto.SubmitClozeAttemptRequest;
import com.lexiflow.quiz.cloze.service.ClozeAttemptAiReviewService;
import com.lexiflow.quiz.cloze.service.ClozeQuizService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@Tag(name = "AI 完形填空接口")
@Validated
@RestController
@RequiredArgsConstructor
public class ClozeQuizController {

    private final ClozeQuizService clozeQuizService;
    private final ClozeAttemptAiReviewService clozeAttemptAiReviewService;

    @Operation(summary = "创建 AI 完形填空任务")
    @PostMapping("/api/v1/ai/cloze-tasks")
    public ApiResponse<CreateClozeTaskResponse> createClozeTask(@Valid @RequestBody CreateClozeTaskRequest request) {
        return ApiResponse.success(clozeQuizService.createClozeTask(AuthContext.currentUserId(), request));
    }

    @Operation(summary = "查询完形填空详情")
    @GetMapping("/api/v1/quizzes/cloze/{quizId}")
    public ApiResponse<ClozeQuizResponse> getQuiz(@PathVariable @Positive Long quizId) {
        return ApiResponse.success(clozeQuizService.getQuiz(AuthContext.currentUserId(), quizId));
    }

    @Operation(summary = "提交完形填空答案")
    @PostMapping("/api/v1/quizzes/cloze/{quizId}/attempts")
    public ApiResponse<ClozeAttemptResponse> submitAttempt(
            @PathVariable @Positive Long quizId,
            @Valid @RequestBody SubmitClozeAttemptRequest request
    ) {
        return ApiResponse.success(clozeQuizService.submitAttempt(AuthContext.currentUserId(), quizId, request));
    }

    @Operation(summary = "查询完形填空作答结果")
    @GetMapping("/api/v1/quizzes/cloze/attempts/{attemptId}")
    public ApiResponse<ClozeAttemptResponse> getAttempt(@PathVariable @Positive Long attemptId) {
        return ApiResponse.success(clozeQuizService.getAttempt(AuthContext.currentUserId(), attemptId));
    }

    @Operation(summary = "查询完形填空 AI 评阅")
    @GetMapping("/api/v1/quizzes/cloze/attempts/{attemptId}/ai-review")
    public ApiResponse<ClozeAttemptAiReviewResponse> getAttemptAiReview(@PathVariable @Positive Long attemptId) {
        return ApiResponse.success(clozeAttemptAiReviewService.getReview(AuthContext.currentUserId(), attemptId));
    }

    @Operation(summary = "流式生成完形填空 AI 评阅")
    @GetMapping(value = "/api/v1/quizzes/cloze/attempts/{attemptId}/ai-review/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> streamAttemptAiReview(
            @PathVariable @Positive Long attemptId,
            @RequestParam(defaultValue = "false") boolean regenerate
    ) {
        Long userId = AuthContext.currentUserId();
        StreamingResponseBody body = outputStream -> clozeAttemptAiReviewService.streamReview(userId, attemptId, regenerate, outputStream);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .header(HttpHeaders.CONNECTION, "keep-alive")
                .header("X-Accel-Buffering", "no")
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(body);
    }
}
