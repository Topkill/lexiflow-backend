package com.lexiflow.ai.content.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.lexiflow.ai.content.domain.AiContentType;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "AI 单词短内容响应")
public record WordAiContentResponse(
        @Schema(description = "是否命中缓存", example = "true") Boolean cacheHit,
        @Schema(description = "AI 内容类型", example = "WORD_QA") AiContentType contentType,
        @Schema(description = "单词 ID") String wordId,
        @Schema(description = "词库 ID") String wordbookId,
        @Schema(description = "结构化内容") JsonNode content,
        @Schema(description = "输出 JSON 结构") JsonNode outputSchema
) {
    public static WordAiContentResponse of(boolean cacheHit, AiContentType contentType, Long wordId, Long wordbookId, JsonNode content) {
        return of(cacheHit, contentType, wordId, wordbookId, content, null);
    }

    public static WordAiContentResponse of(boolean cacheHit, AiContentType contentType, Long wordId, Long wordbookId, JsonNode content, JsonNode outputSchema) {
        return new WordAiContentResponse(cacheHit, contentType, String.valueOf(wordId), String.valueOf(wordbookId), content, outputSchema);
    }
}
