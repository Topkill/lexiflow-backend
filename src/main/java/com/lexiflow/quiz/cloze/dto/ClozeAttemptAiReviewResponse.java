package com.lexiflow.quiz.cloze.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexiflow.async.domain.AsyncTask;
import com.lexiflow.quiz.cloze.domain.ClozeAttemptAiReview;
import com.lexiflow.quiz.cloze.domain.ClozeAttemptAiReviewStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "完形填空 AI 评阅响应")
public record ClozeAttemptAiReviewResponse(
        @Schema(description = "评阅 ID") String reviewId,
        @Schema(description = "作答 ID") String attemptId,
        @Schema(description = "状态") String status,
        @Schema(description = "是否命中缓存") Boolean cacheHit,
        @Schema(description = "评阅内容") JsonNode content,
        @Schema(description = "输出 JSON 结构") JsonNode outputSchema,
        @Schema(description = "打字机展示文本") String displayText,
        @Schema(description = "错误信息") String errorMessage,
        @Schema(description = "异步任务 ID") String taskId,
        @Schema(description = "异步任务状态") String taskStatus
) {
    public static ClozeAttemptAiReviewResponse none(Long attemptId) {
        return new ClozeAttemptAiReviewResponse(null, String.valueOf(attemptId), "NONE", false, null, null, "", null, null, null);
    }

    public static ClozeAttemptAiReviewResponse none(Long attemptId, AsyncTask task) {
        return new ClozeAttemptAiReviewResponse(
                null,
                String.valueOf(attemptId),
                task == null || task.getStatus() == null ? "NONE" : task.getStatus().name(),
                false,
                null,
                null,
                "",
                task == null ? null : task.getErrorMessage(),
                task == null || task.getId() == null ? null : String.valueOf(task.getId()),
                task == null || task.getStatus() == null ? null : task.getStatus().name()
        );
    }

    public static ClozeAttemptAiReviewResponse from(ClozeAttemptAiReview review, ObjectMapper objectMapper) {
        JsonNode contentNode = parseContent(objectMapper, review.getContentJson());
        return of(review, contentNode);
    }

    public static ClozeAttemptAiReviewResponse of(ClozeAttemptAiReview review, JsonNode contentNode) {
        return of(review, contentNode, null);
    }

    public static ClozeAttemptAiReviewResponse of(ClozeAttemptAiReview review, JsonNode contentNode, JsonNode outputSchema) {
        return of(review, contentNode, outputSchema, null);
    }

    public static ClozeAttemptAiReviewResponse of(ClozeAttemptAiReview review, JsonNode contentNode, JsonNode outputSchema, AsyncTask task) {
        boolean cacheHit = review.getStatus() == ClozeAttemptAiReviewStatus.DONE && contentNode != null;
        return new ClozeAttemptAiReviewResponse(
                review.getId() == null ? null : String.valueOf(review.getId()),
                review.getAttemptId() == null ? null : String.valueOf(review.getAttemptId()),
                review.getStatus() == null ? "RUNNING" : review.getStatus().name(),
                cacheHit,
                contentNode,
                outputSchema,
                ClozeAttemptAiReviewDisplayFormatter.format(contentNode),
                review.getErrorMessage(),
                task == null || task.getId() == null ? null : String.valueOf(task.getId()),
                task == null || task.getStatus() == null ? null : task.getStatus().name()
        );
    }

    private static JsonNode parseContent(ObjectMapper objectMapper, String contentJson) {
        if (objectMapper == null || contentJson == null || contentJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(contentJson);
        } catch (Exception ignored) {
            return null;
        }
    }
}
