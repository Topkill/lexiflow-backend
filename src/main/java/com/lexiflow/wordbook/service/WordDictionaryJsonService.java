package com.lexiflow.wordbook.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexiflow.common.error.ErrorCode;
import com.lexiflow.common.exception.BizException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 单词词典 JSON 服务。
 * <p>提供单词标准化、JSON 构建、摘要派生等工具方法。</p>
 */
@Service
@RequiredArgsConstructor
public class WordDictionaryJsonService {

    private static final int PRIMARY_DEFINITION_MAX_LENGTH = 512;

    private final ObjectMapper objectMapper;

    public String normalizeWord(String word) {
        if (!StringUtils.hasText(word)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "单词不能为空");
        }
        return word.trim().toLowerCase(Locale.ROOT);
    }

    public String normalizeRequiredJson(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(ErrorCode.BAD_REQUEST, fieldName + "不能为空");
        }
        return compactJson(value, fieldName);
    }

    public String normalizeOptionalJson(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return compactJson(value, fieldName);
    }

    public WordSummary deriveSummary(String transJson, String primaryPos, String primaryDefinition) {
        String safePrimaryPos = trimToNull(primaryPos);
        String safePrimaryDefinition = trimToNull(primaryDefinition);
        if (StringUtils.hasText(safePrimaryPos) && StringUtils.hasText(safePrimaryDefinition)) {
            return new WordSummary(safePrimaryPos, truncate(safePrimaryDefinition, PRIMARY_DEFINITION_MAX_LENGTH));
        }

        try {
            JsonNode root = objectMapper.readTree(transJson);
            JsonNode first = root.isArray() && !root.isEmpty() ? root.get(0) : root;
            if (!StringUtils.hasText(safePrimaryPos)) {
                safePrimaryPos = textOrNull(first.path("pos"));
            }
            if (!StringUtils.hasText(safePrimaryDefinition)) {
                safePrimaryDefinition = textOrNull(first.path("cn"));
                if (!StringUtils.hasText(safePrimaryDefinition) && first.path("definitions").isArray()) {
                    List<String> definitions = new ArrayList<>();
                    first.path("definitions").forEach(node -> {
                        if (StringUtils.hasText(node.asText())) {
                            definitions.add(node.asText().trim());
                        }
                    });
                    safePrimaryDefinition = definitions.isEmpty() ? null : String.join("；", definitions);
                }
            }
        } catch (Exception ignored) {
            // JSON 已在入库前校验过；这里兜底避免摘要派生影响导入主流程。
        }
        return new WordSummary(
                safePrimaryPos,
                StringUtils.hasText(safePrimaryDefinition) ? truncate(safePrimaryDefinition, PRIMARY_DEFINITION_MAX_LENGTH) : null
        );
    }

    public String buildTransJson(String pos, String definition) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("pos", StringUtils.hasText(pos) ? pos.trim() : "");
        item.put("cn", definition.trim());
        return toJson(List.of(item));
    }

    public String buildSentencesJson(String sentence, String translation) {
        if (!StringUtils.hasText(sentence) && !StringUtils.hasText(translation)) {
            return null;
        }
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("c", StringUtils.hasText(sentence) ? sentence.trim() : "");
        item.put("cn", StringUtils.hasText(translation) ? translation.trim() : "");
        return toJson(List.of(item));
    }

    public String toJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception ex) {
            throw new BizException(ErrorCode.INTERNAL_ERROR);
        }
    }

    public String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new BizException(ErrorCode.INTERNAL_ERROR);
        }
    }

    private String compactJson(String value, String fieldName) {
        try {
            return objectMapper.writeValueAsString(objectMapper.readTree(value));
        } catch (Exception ex) {
            throw new BizException(ErrorCode.BAD_REQUEST, fieldName + "必须是合法 JSON");
        }
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String textOrNull(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() || !StringUtils.hasText(node.asText()) ? null : node.asText().trim();
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    public record WordSummary(String primaryPos, String primaryDefinition) {
    }
}
