package com.lexiflow.quiz.cloze.dto;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.util.StringUtils;

/**
 * 完形填空 AI 评阅逐空点评响应 DTO。
 * <p>包含对每个空的点评和建议。</p>
 *
 * @param blankNo 空格序号
 * @param comment 点评
 * @param tip 学习建议
 */
@Schema(description = "完形填空 AI 评阅逐空点评")
public record ClozeAttemptAiReviewBlankReviewResponse(
        @Schema(description = "空格序号") Integer blankNo,
        @Schema(description = "点评") String comment,
        @Schema(description = "学习建议") String tip
) {
    public static ClozeAttemptAiReviewBlankReviewResponse from(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        Integer blankNo = parseInteger(node.path("blankNo"));
        String comment = normalize(node.path("comment").asText(""));
        String tip = normalize(node.path("tip").asText(""));
        if (blankNo == null && !StringUtils.hasText(comment) && !StringUtils.hasText(tip)) {
            return null;
        }
        return new ClozeAttemptAiReviewBlankReviewResponse(blankNo, comment, tip);
    }

    public static ClozeAttemptAiReviewBlankReviewResponse of(Integer blankNo, String comment, String tip) {
        return new ClozeAttemptAiReviewBlankReviewResponse(blankNo, normalize(comment), normalize(tip));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static Integer parseInteger(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isInt()) {
            return node.asInt();
        }
        String text = node.asText("");
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
