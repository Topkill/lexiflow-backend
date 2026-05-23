package com.lexiflow.quiz.cloze.dto;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;
import org.springframework.util.StringUtils;

@Schema(description = "完形填空 AI 评阅薄弱点")
public record ClozeAttemptAiReviewWeaknessResponse(
        @Schema(description = "标签") String tag,
        @Schema(description = "相关空格序号") List<Integer> blankNos,
        @Schema(description = "点评") String comment
) {
    public static ClozeAttemptAiReviewWeaknessResponse from(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String tag = normalize(node.path("tag").asText(""));
        List<Integer> blankNos = new ArrayList<>();
        JsonNode blankNosNode = node.path("blankNos");
        if (blankNosNode.isArray()) {
            for (JsonNode item : blankNosNode) {
                if (item.isInt()) {
                    blankNos.add(item.asInt());
                } else if (item.isTextual() && StringUtils.hasText(item.asText(""))) {
                    try {
                        blankNos.add(Integer.parseInt(item.asText("").trim()));
                    } catch (NumberFormatException ignored) {
                        // ignore invalid item
                    }
                }
            }
        }
        String comment = normalize(node.path("comment").asText(""));
        if (!StringUtils.hasText(tag) && blankNos.isEmpty() && !StringUtils.hasText(comment)) {
            return null;
        }
        return new ClozeAttemptAiReviewWeaknessResponse(tag, blankNos, comment);
    }

    public static ClozeAttemptAiReviewWeaknessResponse of(String tag, List<Integer> blankNos, String comment) {
        return new ClozeAttemptAiReviewWeaknessResponse(normalize(tag), blankNos == null ? List.of() : List.copyOf(blankNos), normalize(comment));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
