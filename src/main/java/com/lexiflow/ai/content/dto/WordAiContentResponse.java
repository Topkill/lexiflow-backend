package com.lexiflow.ai.content.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.lexiflow.ai.content.domain.AiContentType;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * AI 单词问答响应 DTO
 * <p>
 * 返回 AI 生成的单词问答结果，包含缓存命中信息、结构化内容、输出 Schema 以及异步任务状态。
 * 提供多个静态工厂方法以便在不同场景下构建响应对象。
 * </p>
 */
@Schema(description = "AI 单词问答响应")
public record WordAiContentResponse(
        @Schema(description = "是否命中缓存", example = "true") Boolean cacheHit,
        @Schema(description = "AI 内容类型", example = "WORD_QA") AiContentType contentType,
        @Schema(description = "AI 问答结果 ID") String resultId,
        @Schema(description = "单词 ID") String wordId,
        @Schema(description = "词库 ID") String wordbookId,
        @Schema(description = "结构化内容") JsonNode content,
        @Schema(description = "输出 JSON 结构") JsonNode outputSchema,
        @Schema(description = "异步任务 ID") String taskId,
        @Schema(description = "异步任务状态") String taskStatus,
        @Schema(description = "异步任务消息") String taskMessage
) {
    /**
     * 构建不含输出 Schema 的响应
     */
    public static WordAiContentResponse of(boolean cacheHit, AiContentType contentType, Long wordId, Long wordbookId, JsonNode content) {
        return of(cacheHit, contentType, wordId, wordbookId, content, null);
    }

    /**
     * 构建不含结果 ID 的响应
     */
    public static WordAiContentResponse of(boolean cacheHit, AiContentType contentType, Long wordId, Long wordbookId, JsonNode content, JsonNode outputSchema) {
        return of(cacheHit, contentType, null, wordId, wordbookId, content, outputSchema);
    }

    /**
     * 构建不含异步任务信息的响应
     */
    public static WordAiContentResponse of(boolean cacheHit, AiContentType contentType, Long resultId, Long wordId, Long wordbookId, JsonNode content, JsonNode outputSchema) {
        return of(cacheHit, contentType, resultId, wordId, wordbookId, content, outputSchema, null, null, null);
    }

    /**
     * 构建完整的 AI 单词问答响应
     *
     * @param cacheHit 是否命中缓存
     * @param contentType AI 内容类型
     * @param resultId 问答结果 ID
     * @param wordId 单词 ID
     * @param wordbookId 词库 ID
     * @param content 结构化内容
     * @param outputSchema 输出 JSON Schema
     * @param taskId 异步任务 ID
     * @param taskStatus 异步任务状态
     * @param taskMessage 异步任务消息
     * @return AI 单词问答响应对象
     */
    public static WordAiContentResponse of(
            boolean cacheHit,
            AiContentType contentType,
            Long resultId,
            Long wordId,
            Long wordbookId,
            JsonNode content,
            JsonNode outputSchema,
            Long taskId,
            String taskStatus,
            String taskMessage
    ) {
        return new WordAiContentResponse(
                cacheHit,
                contentType,
                resultId == null ? null : String.valueOf(resultId),
                String.valueOf(wordId),
                String.valueOf(wordbookId),
                content,
                outputSchema,
                taskId == null ? null : String.valueOf(taskId),
                taskStatus,
                taskMessage
        );
    }
}
