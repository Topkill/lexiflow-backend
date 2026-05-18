package com.lexiflow.ai.content.controller;

import com.lexiflow.ai.content.domain.AiContentType;
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
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "AI 单词短内容接口")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/ai/words")
public class WordAiContentController {

    private final WordAiContentService wordAiContentService;

    @Operation(summary = "生成 AI 单词讲解")
    @PostMapping("/{wordId}/explanation")
    public ApiResponse<WordAiContentResponse> generateExplanation(
            @PathVariable @Positive Long wordId,
            @RequestParam @Positive Long wordbookId,
            @RequestParam(defaultValue = "false") boolean regenerate
    ) {
        return ApiResponse.success(wordAiContentService.generateWordContent(AuthContext.currentUserId(), wordbookId, wordId, AiContentType.EXPLANATION, regenerate));
    }

    @Operation(summary = "生成 AI 例句")
    @PostMapping("/{wordId}/examples")
    public ApiResponse<WordAiContentResponse> generateExamples(
            @PathVariable @Positive Long wordId,
            @RequestParam @Positive Long wordbookId,
            @RequestParam(defaultValue = "false") boolean regenerate
    ) {
        return ApiResponse.success(wordAiContentService.generateWordContent(AuthContext.currentUserId(), wordbookId, wordId, AiContentType.EXAMPLES, regenerate));
    }

    @Operation(summary = "生成 AI 记忆法")
    @PostMapping("/{wordId}/mnemonic")
    public ApiResponse<WordAiContentResponse> generateMnemonic(
            @PathVariable @Positive Long wordId,
            @RequestParam @Positive Long wordbookId,
            @RequestParam(defaultValue = "false") boolean regenerate
    ) {
        return ApiResponse.success(wordAiContentService.generateWordContent(AuthContext.currentUserId(), wordbookId, wordId, AiContentType.MNEMONIC, regenerate));
    }

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
}
