package com.lexiflow.ai.core.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexiflow.ai.core.dto.AiChatCompletionResult;
import com.lexiflow.ai.core.dto.AiRuntimeConfig;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
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
        Map<String, Object> request = buildRequest(config, systemPrompt, userPrompt);
        try {
            byte[] responseBytes = restClient.post()
                    .uri(chatCompletionsUrl(config.apiBaseUrl()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(config.useStream() ? MediaType.TEXT_EVENT_STREAM : MediaType.APPLICATION_JSON)
                    .headers(headers -> headers.setBearerAuth(config.apiKey()))
                    .body(request)
                    .retrieve()
                    .body(byte[].class);
            String responseBody = new String(responseBytes == null ? new byte[0] : responseBytes, StandardCharsets.UTF_8);
            return config.useStream() ? parseStreamResponse(responseBody) : parseResponse(responseBody);
        } catch (RestClientResponseException ex) {
            throw new AiClientException(String.valueOf(ex.getStatusCode().value()), abbreviate(ex.getResponseBodyAsString()), ex);
        } catch (AiClientException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AiClientException("CLIENT_ERROR", ex.getMessage(), ex);
        }
    }

    private Map<String, Object> buildRequest(AiRuntimeConfig config, String systemPrompt, String userPrompt) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", config.modelName());
        request.put("temperature", normalizeTemperature(config.temperature()));
        request.put("response_format", Map.of("type", "json_object"));
        request.put("stream", config.useStream());
        request.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
        ));
        return request;
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

    private AiChatCompletionResult parseStreamResponse(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            throw new AiClientException("EMPTY_STREAM", "AI 流式响应为空");
        }
        if (responseBody.stripLeading().startsWith("{")) {
            return parseResponse(responseBody);
        }
        StringBuilder content = new StringBuilder();
        int promptTokens = 0;
        int completionTokens = 0;
        int totalTokens = 0;
        for (String rawLine : responseBody.split("\\r?\\n")) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith(":") || line.startsWith("event:")) {
                continue;
            }
            String data = line.startsWith("data:") ? line.substring(5).trim() : line;
            if (data.isEmpty() || "[DONE]".equals(data)) {
                continue;
            }
            try {
                JsonNode root = objectMapper.readTree(data);
                JsonNode usage = root.path("usage");
                if (usage.isObject()) {
                    promptTokens = usage.path("prompt_tokens").asInt(promptTokens);
                    completionTokens = usage.path("completion_tokens").asInt(completionTokens);
                    totalTokens = usage.path("total_tokens").asInt(totalTokens);
                }
                JsonNode choices = root.path("choices");
                if (!choices.isArray()) {
                    continue;
                }
                for (JsonNode choice : choices) {
                    JsonNode delta = choice.path("delta");
                    String deltaContent = delta.path("content").asText("");
                    if (!deltaContent.isEmpty()) {
                        content.append(deltaContent);
                    }
                    String messageContent = choice.path("message").path("content").asText("");
                    if (!messageContent.isEmpty()) {
                        content.append(messageContent);
                    }
                }
            } catch (Exception ex) {
                throw new AiClientException("STREAM_PARSE_ERROR", "AI 流式响应解析失败", ex);
            }
        }
        if (content.isEmpty()) {
            throw new AiClientException("EMPTY_CONTENT", "AI 流式响应内容为空");
        }
        if (totalTokens == 0) {
            totalTokens = promptTokens + completionTokens;
        }
        return new AiChatCompletionResult(content.toString(), promptTokens, completionTokens, totalTokens);
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
