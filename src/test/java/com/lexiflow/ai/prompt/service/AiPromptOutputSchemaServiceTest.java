package com.lexiflow.ai.prompt.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexiflow.ai.prompt.domain.AiPromptFeatureType;
import org.junit.jupiter.api.Test;

class AiPromptOutputSchemaServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AiPromptOutputSchemaService service = new AiPromptOutputSchemaService(objectMapper);

    @Test
    void defaultWordQaAndClozeReviewSchemasShouldIncludeGrammarTip() throws Exception {
        JsonNode wordQaSchema = objectMapper.readTree(service.defaultSchemaJson(AiPromptFeatureType.WORD_QA));
        JsonNode clozeReviewSchema = objectMapper.readTree(service.defaultSchemaJson(AiPromptFeatureType.CLOZE_REVIEW));

        assertThat(wordQaSchema.path("grammarTip").isTextual()).isTrue();
        assertThat(clozeReviewSchema.path("grammarTip").isTextual()).isTrue();
    }

    @Test
    void resolveSchemaShouldEnrichGrammarTipForLegacySchemas() throws Exception {
        String wordQaSchemaJson = """
                {
                  "answer": "",
                  "keyPoints": [],
                  "relatedWords": [],
                  "followUps": []
                }
                """;
        String clozeReviewSchemaJson = """
                {
                  "overall": "",
                  "mistakeTags": [],
                  "strengths": [],
                  "weaknesses": [
                    {
                      "tag": "",
                      "blankNos": [],
                      "comment": ""
                    }
                  ],
                  "suggestions": [],
                  "blankReviews": [
                    {
                      "blankNo": 0,
                      "comment": "",
                      "tip": ""
                    }
                  ]
                }
                """;

        JsonNode wordQaSchema = objectMapper.readTree(service.resolveSchemaJson(AiPromptFeatureType.WORD_QA, wordQaSchemaJson));
        JsonNode clozeReviewSchema = objectMapper.readTree(service.resolveSchemaJson(AiPromptFeatureType.CLOZE_REVIEW, clozeReviewSchemaJson));

        assertThat(wordQaSchema.path("grammarTip").asText()).isEmpty();
        assertThat(clozeReviewSchema.path("grammarTip").asText()).isEmpty();
    }
}
