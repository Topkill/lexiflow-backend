package com.lexiflow.ai.core.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexiflow.ai.core.dto.AiChatCompletionResult;
import com.lexiflow.ai.core.dto.AiRuntimeConfig;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class OpenAiCompatibleClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(60);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public OpenAiCompatibleClient(ObjectMapper objectMapper) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
        this.objectMapper = objectMapper;
    }

    public AiChatCompletionResult chatJson(AiRuntimeConfig config, String systemPrompt, String userPrompt) {
        Map<String, Object> request = Map.of(
                "model", config.modelName(),
                "temperature", normalizeTemperature(config.temperature()),
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                )
        );
        try {
            String responseBody = restClient.post()
                    .uri(chatCompletionsUrl(config.apiBaseUrl()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> headers.setBearerAuth(config.apiKey()))
                    .body(request)
                    .retrieve()
                    .body(String.class);
            return parseResponse(responseBody);
        } catch (RestClientResponseException ex) {
            throw new AiClientException(String.valueOf(ex.getStatusCode().value()), abbreviate(ex.getResponseBodyAsString()), ex);
        } catch (AiClientException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AiClientException("CLIENT_ERROR", ex.getMessage(), ex);
        }
    }

    private AiChatCompletionResult parseResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                throw new AiClientException("EMPTY_CHOICES", "AI 响应中没有 choices");
            }
            String content = choices.get(0).path("message").path("content").asText();
            if (content == null || content.isBlank()) {
                throw new AiClientException("EMPTY_CONTENT", "AI 响应内容为空");
            }
            JsonNode usage = root.path("usage");
            int promptTokens = usage.path("prompt_tokens").asInt(0);
            int completionTokens = usage.path("completion_tokens").asInt(0);
            int totalTokens = usage.path("total_tokens").asInt(promptTokens + completionTokens);
            return new AiChatCompletionResult(content, promptTokens, completionTokens, totalTokens);
        } catch (AiClientException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AiClientException("PARSE_ERROR", "AI 响应解析失败", ex);
        }
    }

    private String chatCompletionsUrl(String apiBaseUrl) {
        String baseUrl = apiBaseUrl.trim();
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        if (baseUrl.endsWith("/chat/completions")) {
            return baseUrl;
        }
        return baseUrl + "/chat/completions";
    }

    private BigDecimal normalizeTemperature(BigDecimal temperature) {
        return temperature == null ? new BigDecimal("0.70") : temperature;
    }

    private String abbreviate(String message) {
        if (message == null || message.isBlank()) {
            return "AI 调用失败";
        }
        return message.length() > 512 ? message.substring(0, 512) : message;
    }
}
