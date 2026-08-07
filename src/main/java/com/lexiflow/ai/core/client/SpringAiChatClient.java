package com.lexiflow.ai.core.client;

import com.lexiflow.ai.core.dto.AiChatCompletionResult;
import com.lexiflow.ai.core.dto.AiRuntimeConfig;
import io.netty.channel.ChannelOption;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.netty.http.client.HttpClient;

/**
 * Spring AI 聊天客户端
 * <p>
 * 基于 Spring AI 框架封装的 AI 聊天客户端，支持同步 JSON 调用和流式 SSE 调用。
 * 自动处理 API 端点解析、请求构建、响应提取、Token 统计以及异常转换。
 * 支持 OpenAI 兼容接口，强制 JSON 对象格式输出。
 * </p>
 */
@Component
public class SpringAiChatClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(60);
    private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";
    private static final RetryTemplate NO_RETRY_TEMPLATE = RetryTemplate.builder().maxAttempts(1).build();

    /**
     * 同步调用 AI 接口并返回 JSON 结果
     * <p>
     * 如果配置启用流式，则内部转为流式调用但忽略增量输出。
     * </p>
     *
     * @param config AI 运行时配置
     * @param systemPrompt 系统提示词
     * @param userPrompt 用户提示词
     * @return AI 聊天完成结果
     * @throws AiClientException 当调用失败或响应内容为空时
     */
    public AiChatCompletionResult chatJson(AiRuntimeConfig config, String systemPrompt, String userPrompt) {
        if (config.useStream()) {
            return chatJsonStream(config, systemPrompt, userPrompt, delta -> {
            });
        }
        try {
            ChatResponse response = chatModel(config).call(prompt(config, systemPrompt, userPrompt));
            String content = extractContent(response);
            if (!StringUtils.hasText(content)) {
                throw new AiClientException("EMPTY_CONTENT", "AI 响应内容为空");
            }
            return toResult(content, response);
        } catch (AiClientException ex) {
            throw ex;
        } catch (Exception ex) {
            throw toAiClientException(ex);
        }
    }

    /**
     * 流式调用 AI 接口并返回 JSON 结果
     * <p>
     * 在流式接收过程中通过 deltaHandler 实时回调增量文本片段。
     * </p>
     *
     * @param config AI 运行时配置
     * @param systemPrompt 系统提示词
     * @param userPrompt 用户提示词
     * @param deltaHandler 流式增量文本处理器
     * @return AI 聊天完成结果
     * @throws AiClientException 当调用失败或响应内容为空时
     */
    public AiChatCompletionResult chatJsonStream(AiRuntimeConfig config, String systemPrompt, String userPrompt, AiStreamDeltaHandler deltaHandler) {
        StringBuilder content = new StringBuilder();
        TokenUsageAccumulator usage = new TokenUsageAccumulator();
        try {
            for (ChatResponse response : chatModel(config).stream(prompt(config, systemPrompt, userPrompt)).toIterable()) {
                usage.accept(response);
                String delta = extractContent(response);
                if (delta == null || delta.isEmpty()) {
                    continue;
                }
                content.append(delta);
                emitDelta(deltaHandler, delta);
            }
        } catch (AiClientException ex) {
            throw ex;
        } catch (Exception ex) {
            throw toAiClientException(ex);
        }
        if (content.isEmpty()) {
            throw new AiClientException("EMPTY_CONTENT", "AI 流式响应内容为空");
        }
        return new AiChatCompletionResult(content.toString(), usage.promptTokens(), usage.completionTokens(), usage.totalTokens());
    }

    private OpenAiChatModel chatModel(AiRuntimeConfig config) {
        Endpoint endpoint = endpoint(config.apiBaseUrl());
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(endpoint.baseUrl())
                .completionsPath(endpoint.completionsPath())
                .apiKey(config.apiKey())
                .restClientBuilder(restClientBuilder())
                .webClientBuilder(webClientBuilder())
                .build();
        return OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(chatOptions(config))
                .retryTemplate(NO_RETRY_TEMPLATE)
                .build();
    }

    private OpenAiChatOptions chatOptions(AiRuntimeConfig config) {
        return OpenAiChatOptions.builder()
                .model(config.modelName())
                .temperature(normalizeTemperature(config.temperature()))
                .responseFormat(ResponseFormat.builder().type(ResponseFormat.Type.JSON_OBJECT).build())
                .build();
    }

    private RestClient.Builder restClientBuilder() {
        java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        return RestClient.builder().requestFactory(requestFactory);
    }

    private WebClient.Builder webClientBuilder() {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, Math.toIntExact(CONNECT_TIMEOUT.toMillis()))
                .responseTimeout(CONNECT_TIMEOUT.plus(READ_TIMEOUT));
        return WebClient.builder().clientConnector(new ReactorClientHttpConnector(httpClient));
    }

    private Prompt prompt(AiRuntimeConfig config, String systemPrompt, String userPrompt) {
        return new Prompt(List.of(
                new SystemMessage(systemPrompt),
                new UserMessage(userPrompt)
        ), chatOptions(config));
    }

    private Endpoint endpoint(String apiBaseUrl) {
        if (!StringUtils.hasText(apiBaseUrl)) {
            throw new AiClientException("INVALID_BASE_URL", "AI Base URL 不能为空");
        }
        String baseUrl = apiBaseUrl.trim();
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        String lowerBaseUrl = baseUrl.toLowerCase();
        if (lowerBaseUrl.endsWith(CHAT_COMPLETIONS_PATH)) {
            return new Endpoint(baseUrl.substring(0, baseUrl.length() - CHAT_COMPLETIONS_PATH.length()), CHAT_COMPLETIONS_PATH);
        }
        return new Endpoint(baseUrl, CHAT_COMPLETIONS_PATH);
    }

    private String extractContent(ChatResponse response) {
        if (response == null) {
            return "";
        }
        Generation generation = response.getResult();
        if (generation == null || generation.getOutput() == null) {
            return "";
        }
        return generation.getOutput().getText();
    }

    private AiChatCompletionResult toResult(String content, ChatResponse response) {
        Usage usage = response == null || response.getMetadata() == null ? null : response.getMetadata().getUsage();
        int promptTokens = usage == null || usage.getPromptTokens() == null ? 0 : usage.getPromptTokens();
        int completionTokens = usage == null || usage.getCompletionTokens() == null ? 0 : usage.getCompletionTokens();
        int totalTokens = usage == null || usage.getTotalTokens() == null ? promptTokens + completionTokens : usage.getTotalTokens();
        return new AiChatCompletionResult(content, promptTokens, completionTokens, totalTokens);
    }

    private void emitDelta(AiStreamDeltaHandler deltaHandler, String delta) {
        try {
            deltaHandler.onDelta(delta);
        } catch (Exception ex) {
            throw new AiClientException("STREAM_HANDLER_ERROR", "AI 流式响应处理失败", ex);
        }
    }

    private double normalizeTemperature(BigDecimal temperature) {
        return (temperature == null ? new BigDecimal("0.70") : temperature).doubleValue();
    }

    private String abbreviate(String message) {
        if (!StringUtils.hasText(message)) {
            return "AI 调用失败";
        }
        return message.length() > 512 ? message.substring(0, 512) : message;
    }

    private AiClientException toAiClientException(Exception ex) {
        RestClientResponseException restError = findCause(ex, RestClientResponseException.class);
        if (restError != null) {
            return new AiClientException(String.valueOf(restError.getStatusCode().value()),
                    abbreviate(restError.getResponseBodyAsString()), restError);
        }
        WebClientResponseException webError = findCause(ex, WebClientResponseException.class);
        if (webError != null) {
            return new AiClientException(String.valueOf(webError.getStatusCode().value()),
                    abbreviate(webError.getResponseBodyAsString()), webError);
        }
        String statusCode = statusCodePrefix(ex.getMessage());
        if (statusCode != null) {
            return new AiClientException(statusCode, abbreviate(ex.getMessage()), ex);
        }
        return new AiClientException("CLIENT_ERROR", abbreviate(ex.getMessage()), ex);
    }

    private <T extends Throwable> T findCause(Throwable throwable, Class<T> type) {
        Throwable current = throwable;
        while (current != null) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            Throwable cause = current.getCause();
            if (cause == current) {
                break;
            }
            current = cause;
        }
        return null;
    }

    private String statusCodePrefix(String message) {
        if (!StringUtils.hasText(message) || message.length() < 6 || message.charAt(3) != ' ') {
            return null;
        }
        for (int i = 0; i < 3; i++) {
            if (!Character.isDigit(message.charAt(i))) {
                return null;
            }
        }
        return message.substring(0, 3);
    }

    private record Endpoint(String baseUrl, String completionsPath) {
    }

    private static final class TokenUsageAccumulator {

        private int promptTokens;
        private int completionTokens;
        private int totalTokens;

        private void accept(ChatResponse response) {
            Usage usage = response == null || response.getMetadata() == null ? null : response.getMetadata().getUsage();
            if (usage == null) {
                return;
            }
            promptTokens = usage.getPromptTokens() == null ? promptTokens : usage.getPromptTokens();
            completionTokens = usage.getCompletionTokens() == null ? completionTokens : usage.getCompletionTokens();
            totalTokens = usage.getTotalTokens() == null ? totalTokens : usage.getTotalTokens();
        }

        private int promptTokens() {
            return promptTokens;
        }

        private int completionTokens() {
            return completionTokens;
        }

        private int totalTokens() {
            return totalTokens == 0 ? promptTokens + completionTokens : totalTokens;
        }
    }
}
