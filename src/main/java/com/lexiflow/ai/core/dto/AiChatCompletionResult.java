package com.lexiflow.ai.core.dto;

public record AiChatCompletionResult(
        String content,
        int promptTokens,
        int completionTokens,
        int totalTokens
) {
}
