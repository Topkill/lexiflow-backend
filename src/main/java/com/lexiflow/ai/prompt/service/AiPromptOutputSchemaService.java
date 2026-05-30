package com.lexiflow.ai.prompt.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lexiflow.ai.prompt.domain.AiPromptFeatureType;
import com.lexiflow.common.error.ErrorCode;
import com.lexiflow.common.exception.BizException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AiPromptOutputSchemaService {

    private static final Pattern FIELD_KEY_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9_]{0,63}$");
    private static final int MAX_FIELDS = 40;
    private static final Map<AiPromptFeatureType, List<RequiredFieldSpec>> REQUIRED_FIELDS = requiredFields();

    private final ObjectMapper objectMapper;

    public JsonNode defaultSchema(AiPromptFeatureType featureType) {
        return schemaNode(defaultSchemaJson(featureType));
    }

    public String defaultSchemaJson(AiPromptFeatureType featureType) {
        ObjectNode schema = objectMapper.createObjectNode();
        switch (featureType) {
            case WORD_QA -> {
                schema.put("answer", "");
                schema.set("keyPoints", objectMapper.createArrayNode());
                schema.set("relatedWords", objectMapper.createArrayNode());
                schema.set("followUps", objectMapper.createArrayNode());
                schema.put("grammarTip", "");
            }
            case CLOZE_QUIZ -> {
                schema.put("title", "");
                schema.put("passage", "");
                schema.put("passageZh", "");
                ArrayNode explanations = schema.putArray("explanations");
                ObjectNode explanation = explanations.addObject();
                explanation.put("word", "");
                explanation.put("usedForm", "");
                explanation.put("usedPos", "");
                explanation.put("definitionZh", "");
                explanation.put("reasonZh", "");
            }
            case CLOZE_REVIEW -> {
                schema.put("overall", "");
                schema.set("mistakeTags", objectMapper.createArrayNode());
                schema.set("strengths", objectMapper.createArrayNode());
                ArrayNode weaknesses = schema.putArray("weaknesses");
                ObjectNode weakness = weaknesses.addObject();
                weakness.put("tag", "");
                weakness.set("blankNos", objectMapper.createArrayNode());
                weakness.put("comment", "");
                schema.set("suggestions", objectMapper.createArrayNode());
                ArrayNode blankReviews = schema.putArray("blankReviews");
                ObjectNode blankReview = blankReviews.addObject();
                blankReview.put("blankNo", 0);
                blankReview.put("comment", "");
                blankReview.put("tip", "");
                schema.put("grammarTip", "");
            }
            default -> throw new BizException(ErrorCode.BAD_REQUEST, "不支持的 AI 功能类型");
        }
        return toJson(schema);
    }

    public String resolveSchemaJson(AiPromptFeatureType featureType, String outputSchemaJson) {
        if (!StringUtils.hasText(outputSchemaJson)) {
            return defaultSchemaJson(featureType);
        }
        return normalizeAndValidate(featureType, parse(outputSchemaJson));
    }

    public String normalizeAndValidate(AiPromptFeatureType featureType, JsonNode schemaNode) {
        JsonNode schema = schemaNode == null || schemaNode.isNull() ? defaultSchema(featureType) : schemaNode;
        if (!schema.isObject() || schema.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "输出 JSON 结构必须是非空对象");
        }
        if (schema.size() > MAX_FIELDS) {
            throw new BizException(ErrorCode.BAD_REQUEST, "输出 JSON 字段数量不能超过 " + MAX_FIELDS);
        }

        ObjectNode normalized = objectMapper.createObjectNode();
        schema.fields().forEachRemaining(entry -> {
            String key = entry.getKey();
            if (!FIELD_KEY_PATTERN.matcher(key).matches()) {
                throw new BizException(ErrorCode.BAD_REQUEST, "输出 JSON 字段名只能使用字母、数字和下划线，且必须以字母开头");
            }
            normalized.set(key, entry.getValue());
        });
        enrichFeatureSchema(featureType, normalized);
        if (normalized.size() > MAX_FIELDS) {
            throw new BizException(ErrorCode.BAD_REQUEST, "输出 JSON 字段数量不能超过 " + MAX_FIELDS);
        }

        for (RequiredFieldSpec spec : requiredSpecs(featureType)) {
            JsonNode value = normalized.get(spec.key());
            if (value == null || value.isMissingNode()) {
                throw new BizException(ErrorCode.BAD_REQUEST, "输出 JSON 结构缺少字段：" + spec.key());
            }
            if (!matchesKind(value, spec.kind())) {
                throw new BizException(ErrorCode.BAD_REQUEST, "字段 " + spec.key() + " 的 JSON 类型不正确");
            }
            validateNestedFields(spec, value);
        }
        return toJson(normalized);
    }

    private void enrichFeatureSchema(AiPromptFeatureType featureType, ObjectNode normalized) {
        switch (featureType) {
            case WORD_QA, CLOZE_REVIEW -> enrichGrammarTip(normalized);
            case CLOZE_QUIZ -> enrichClozeQuizSchema(normalized);
            default -> {
            }
        }
    }

    private void enrichGrammarTip(ObjectNode normalized) {
        if (!normalized.has("grammarTip")) {
            normalized.put("grammarTip", "");
        }
    }

    private void enrichClozeQuizSchema(ObjectNode normalized) {
        JsonNode explanations = normalized.get("explanations");
        if (explanations == null || !explanations.isArray() || explanations.isEmpty() || !explanations.get(0).isObject()) {
            return;
        }
        ObjectNode explanation = (ObjectNode) explanations.get(0);
        if (!explanation.has("usedForm")) {
            explanation.put("usedForm", "");
        }
    }

    public String buildOutputFormatPrompt(String outputSchemaJson) {
        JsonNode schema = schemaNode(outputSchemaJson);
        return "输出 JSON 格式：请只输出一个 JSON 对象，字段和嵌套结构如下，不要输出 JSON 外文本：\n"
                + toPrettyJson(schema);
    }

    public JsonNode schemaNode(String outputSchemaJson) {
        return parse(resolveText(outputSchemaJson));
    }

    private String resolveText(String outputSchemaJson) {
        if (!StringUtils.hasText(outputSchemaJson)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "输出 JSON 结构不能为空");
        }
        return outputSchemaJson;
    }

    private boolean matchesKind(JsonNode value, JsonValueKind kind) {
        return switch (kind) {
            case STRING -> value.isTextual();
            case ARRAY -> value.isArray();
            case OBJECT -> value.isObject();
            case NUMBER -> value.isNumber();
            case BOOLEAN -> value.isBoolean();
            case ANY -> true;
        };
    }

    private void validateNestedFields(RequiredFieldSpec spec, JsonNode value) {
        if (spec.arrayItemFields().isEmpty()) {
            return;
        }
        if (!value.isArray() || value.isEmpty() || !value.get(0).isObject()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "字段 " + spec.key() + " 必须提供数组元素对象示例");
        }
        JsonNode item = value.get(0);
        for (String field : spec.arrayItemFields()) {
            if (!item.has(field)) {
                throw new BizException(ErrorCode.BAD_REQUEST, "字段 " + spec.key() + " 的数组元素缺少字段：" + field);
            }
        }
    }

    private JsonNode parse(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception ex) {
            throw new BizException(ErrorCode.BAD_REQUEST, "输出 JSON 结构不是合法 JSON");
        }
    }

    private List<RequiredFieldSpec> requiredSpecs(AiPromptFeatureType featureType) {
        List<RequiredFieldSpec> specs = REQUIRED_FIELDS.get(featureType);
        if (specs == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "不支持的 AI 功能类型");
        }
        return specs;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new BizException(ErrorCode.INTERNAL_ERROR);
        }
    }

    private String toPrettyJson(Object value) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (Exception ex) {
            throw new BizException(ErrorCode.INTERNAL_ERROR);
        }
    }

    private static Map<AiPromptFeatureType, List<RequiredFieldSpec>> requiredFields() {
        Map<AiPromptFeatureType, List<RequiredFieldSpec>> fields = new EnumMap<>(AiPromptFeatureType.class);
        fields.put(AiPromptFeatureType.WORD_QA, List.of(
                new RequiredFieldSpec("answer", JsonValueKind.STRING),
                new RequiredFieldSpec("keyPoints", JsonValueKind.ARRAY),
                new RequiredFieldSpec("relatedWords", JsonValueKind.ARRAY),
                new RequiredFieldSpec("followUps", JsonValueKind.ARRAY),
                new RequiredFieldSpec("grammarTip", JsonValueKind.STRING)
        ));
        fields.put(AiPromptFeatureType.CLOZE_QUIZ, List.of(
                new RequiredFieldSpec("title", JsonValueKind.STRING),
                new RequiredFieldSpec("passage", JsonValueKind.STRING),
                new RequiredFieldSpec("passageZh", JsonValueKind.STRING),
                new RequiredFieldSpec("explanations", JsonValueKind.ARRAY, Set.of("word", "usedForm", "usedPos", "definitionZh", "reasonZh"))
        ));
        fields.put(AiPromptFeatureType.CLOZE_REVIEW, List.of(
                new RequiredFieldSpec("overall", JsonValueKind.STRING),
                new RequiredFieldSpec("mistakeTags", JsonValueKind.ARRAY),
                new RequiredFieldSpec("strengths", JsonValueKind.ARRAY),
                new RequiredFieldSpec("weaknesses", JsonValueKind.ARRAY, Set.of("tag", "blankNos", "comment")),
                new RequiredFieldSpec("suggestions", JsonValueKind.ARRAY),
                new RequiredFieldSpec("blankReviews", JsonValueKind.ARRAY, Set.of("blankNo", "comment", "tip")),
                new RequiredFieldSpec("grammarTip", JsonValueKind.STRING)
        ));
        return fields;
    }

    private enum JsonValueKind {
        STRING,
        ARRAY,
        OBJECT,
        NUMBER,
        BOOLEAN,
        ANY
    }

    private record RequiredFieldSpec(String key, JsonValueKind kind, Set<String> arrayItemFields) {
        private RequiredFieldSpec(String key, JsonValueKind kind) {
            this(key, kind, Set.of());
        }
    }
}
