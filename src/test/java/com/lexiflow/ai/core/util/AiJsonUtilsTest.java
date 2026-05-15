package com.lexiflow.ai.core.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class AiJsonUtilsTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void parseObjectShouldHandleMarkdownFence() {
        JsonNode node = AiJsonUtils.parseObject(objectMapper, "```json\n{\"title\":\"ok\"}\n```");

        assertThat(node.path("title").asText()).isEqualTo("ok");
    }

    @Test
    void parseObjectShouldIgnoreThinkingPrefix() {
        JsonNode node = AiJsonUtils.parseObject(objectMapper, "<think>先思考</think>\n{\"title\":\"ok\"}");

        assertThat(node.path("title").asText()).isEqualTo("ok");
    }

    @Test
    void extractObjectJsonShouldStopAtMatchingBrace() {
        String json = AiJsonUtils.extractObjectJson("前缀 {\"text\":\"value { inside }\",\"ok\":true} 后缀 {\"ignored\":true}");

        assertThat(json).isEqualTo("{\"text\":\"value { inside }\",\"ok\":true}");
    }
}
