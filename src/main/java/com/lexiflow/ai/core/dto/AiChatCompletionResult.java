package com.lexiflow.ai.core.dto;

/**
 * AI 聊天完成结果
 * <p>
 * 封装 AI 调用的返回内容和 Token 消耗统计。
 * </p>
 *
 * @param content AI 生成的文本内容
 * @param promptTokens 提示词 Token 数
 * @param completionTokens 完成内容 Token 数
 * @param totalTokens 总 Token 数
 */
public record AiChatCompletionResult(
        String content,
        int promptTokens,
        int completionTokens,
        int totalTokens
) {
}
