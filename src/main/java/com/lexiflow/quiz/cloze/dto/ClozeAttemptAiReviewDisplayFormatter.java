package com.lexiflow.quiz.cloze.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import org.springframework.util.StringUtils;

public final class ClozeAttemptAiReviewDisplayFormatter {

    private ClozeAttemptAiReviewDisplayFormatter() {
    }

    public static String format(JsonNode content) {
        if (content == null || content.isMissingNode() || content.isNull()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        appendHeading(builder, "总体评价");
        appendLine(builder, fallback(content.path("overall").asText("暂无总体评价。")));

        List<String> mistakeTags = normalizeTexts(content.path("mistakeTags"));
        if (!mistakeTags.isEmpty()) {
            appendBlankLine(builder);
            appendHeading(builder, "错因标签");
            appendLine(builder, String.join(" / ", wrapInlineCode(mistakeTags)));
        }

        List<String> strengths = normalizeTexts(content.path("strengths"));
        if (!strengths.isEmpty()) {
            appendBlankLine(builder);
            appendHeading(builder, "亮点");
            for (String item : strengths) {
                appendLine(builder, "- " + item);
            }
        }

        List<ClozeAttemptAiReviewWeaknessResponse> weaknesses = normalizeWeaknesses(content.path("weaknesses"));
        if (!weaknesses.isEmpty()) {
            appendBlankLine(builder);
            appendHeading(builder, "需要注意");
            for (ClozeAttemptAiReviewWeaknessResponse weakness : weaknesses) {
                StringJoiner joiner = new StringJoiner("、");
                weakness.blankNos().forEach(no -> joiner.add("第" + no + "空"));
                String prefix = StringUtils.hasText(weakness.tag()) ? "**" + weakness.tag() + "**：" : "";
                String blanks = weakness.blankNos().isEmpty() ? "" : "（" + joiner + "）";
                appendLine(builder, "- " + prefix + weakness.comment() + blanks);
            }
        }

        List<String> suggestions = normalizeTexts(content.path("suggestions"));
        if (!suggestions.isEmpty()) {
            appendBlankLine(builder);
            appendHeading(builder, "学习建议");
            int index = 1;
            for (String item : suggestions) {
                appendLine(builder, index++ + ". " + item);
            }
        }

        List<ClozeAttemptAiReviewBlankReviewResponse> blankReviews = normalizeBlankReviews(content.path("blankReviews"));
        if (!blankReviews.isEmpty()) {
            appendBlankLine(builder);
            appendHeading(builder, "逐空提醒");
            for (ClozeAttemptAiReviewBlankReviewResponse item : blankReviews) {
                String title = item.blankNo() == null ? "某一空" : "第" + item.blankNo() + "空";
                appendLine(builder, "- **" + title + "**：" + item.comment());
                if (StringUtils.hasText(item.tip())) {
                    appendLine(builder, "  - 建议：" + item.tip());
                }
            }
        }
        return builder.toString().trim();
    }

    private static void appendHeading(StringBuilder builder, String text) {
        appendLine(builder, "## " + text);
    }

    private static List<String> wrapInlineCode(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<String> wrapped = new ArrayList<>(values.size());
        for (String value : values) {
            wrapped.add("`" + value + "`");
        }
        return wrapped;
    }

    private static void appendLine(StringBuilder builder, String text) {
        if (builder.length() > 0) {
            builder.append('\n');
        }
        builder.append(text);
    }

    private static void appendBlankLine(StringBuilder builder) {
        if (builder.length() > 0) {
            builder.append('\n').append('\n');
        }
    }

    private static String fallback(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private static List<String> normalizeTexts(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            String text = item.asText("");
            if (StringUtils.hasText(text)) {
                values.add(text.trim());
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
}
