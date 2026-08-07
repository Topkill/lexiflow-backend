package com.lexiflow.quiz.cloze.dto;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;
import org.springframework.util.StringUtils;

/**
 * 完形填空 AI 评阅内容响应 DTO。
 * <p>包含 AI 评阅的完整内容，包括总体评价、错因标签、亮点、薄弱点、学习建议、逐空点评和语法小知识。</p>
 *
 * @param overall 总体评价
 * @param mistakeTags 错因标签
 * @param strengths 亮点
 * @param weaknesses 薄弱点
 * @param suggestions 学习建议
 * @param blankReviews 逐空点评
 * @param grammarTip 语法小知识
 */
@Schema(description = "完形填空 AI 评阅内容")
public record ClozeAttemptAiReviewContentResponse(
        @Schema(description = "总体评价") String overall,
        @Schema(description = "错因标签") List<String> mistakeTags,
        @Schema(description = "亮点") List<String> strengths,
        @Schema(description = "薄弱点") List<ClozeAttemptAiReviewWeaknessResponse> weaknesses,
        @Schema(description = "学习建议") List<String> suggestions,
        @Schema(description = "逐空点评") List<ClozeAttemptAiReviewBlankReviewResponse> blankReviews,
        @Schema(description = "语法小知识") String grammarTip
) {
    public static ClozeAttemptAiReviewContentResponse from(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String overall = normalize(node.path("overall").asText(""));
        List<String> mistakeTags = normalizeTexts(node.path("mistakeTags"));
        List<String> strengths = normalizeTexts(node.path("strengths"));
        List<ClozeAttemptAiReviewWeaknessResponse> weaknesses = normalizeWeaknesses(node.path("weaknesses"));
        List<String> suggestions = normalizeTexts(node.path("suggestions"));
        List<ClozeAttemptAiReviewBlankReviewResponse> blankReviews = normalizeBlankReviews(node.path("blankReviews"));
        String grammarTip = normalize(node.path("grammarTip").asText(""));
        if (!StringUtils.hasText(overall)
                && mistakeTags.isEmpty()
                && strengths.isEmpty()
                && weaknesses.isEmpty()
                && suggestions.isEmpty()
                && blankReviews.isEmpty()
                && !StringUtils.hasText(grammarTip)) {
            return null;
        }
        return new ClozeAttemptAiReviewContentResponse(overall, mistakeTags, strengths, weaknesses, suggestions, blankReviews, grammarTip);
    }

    private static List<String> normalizeTexts(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            String text = normalize(item.asText(""));
            if (StringUtils.hasText(text)) {
                values.add(text);
            }
        }
        return values;
    }

    private static List<ClozeAttemptAiReviewWeaknessResponse> normalizeWeaknesses(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<ClozeAttemptAiReviewWeaknessResponse> values = new ArrayList<>();
        for (JsonNode item : node) {
            ClozeAttemptAiReviewWeaknessResponse weakness = ClozeAttemptAiReviewWeaknessResponse.from(item);
            if (weakness != null) {
                values.add(weakness);
            }
        }
        return values;
    }

    private static List<ClozeAttemptAiReviewBlankReviewResponse> normalizeBlankReviews(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<ClozeAttemptAiReviewBlankReviewResponse> values = new ArrayList<>();
        for (JsonNode item : node) {
            ClozeAttemptAiReviewBlankReviewResponse review = ClozeAttemptAiReviewBlankReviewResponse.from(item);
            if (review != null) {
                values.add(review);
            }
        }
        return values;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
