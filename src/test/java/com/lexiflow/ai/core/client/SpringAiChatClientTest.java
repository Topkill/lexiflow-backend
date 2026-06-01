package com.lexiflow.ai.core.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.lexiflow.ai.core.dto.AiChatCompletionResult;
import com.lexiflow.ai.core.dto.AiConfigScope;
import com.lexiflow.ai.core.dto.AiRuntimeConfig;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class SpringAiChatClientTest {

    private final SpringAiChatClient client = new SpringAiChatClient();

    @Test
    void chatJsonShouldCallOpenAiCompatibleEndpointThroughSpringAi() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        String responseBody = """
                {
                  "id": "chatcmpl-test",
                  "object": "chat.completion",
                  "created": 0,
                  "model": "test-model",
                  "choices": [
                    {
                      "index": 0,
                      "message": {
                        "role": "assistant",
                        "content": "{\\"answer\\":\\"ok\\"}"
                      },
                      "finish_reason": "stop"
                    }
                  ],
                  "usage": {
                    "prompt_tokens": 3,
                    "completion_tokens": 4,
                    "total_tokens": 7
                  }
                }
        """;

        try (TestAiServer server = TestAiServer.json(responseBody, requestBody)) {
            AiChatCompletionResult result = client.chatJson(runtimeConfig(server.chatCompletionsUrl(), false), "system", "user");

            assertThat(result.content()).isEqualTo("{\"answer\":\"ok\"}");
            assertThat(result.promptTokens()).isEqualTo(3);
            assertThat(result.completionTokens()).isEqualTo(4);
            assertThat(result.totalTokens()).isEqualTo(7);
            assertThat(requestBody.get()).contains("\"response_format\":{\"type\":\"json_object\"");
        }
    }

    @Test
    void chatJsonShouldUseStreamWhenConfigEnabled() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        String responseBody = """
                data: {"id":"chatcmpl-test","object":"chat.completion.chunk","created":0,"model":"test-model","choices":[{"index":0,"delta":{"role":"assistant","content":"{\\"answer\\":\\""},"finish_reason":null}]}

                data: {"id":"chatcmpl-test","object":"chat.completion.chunk","created":0,"model":"test-model","choices":[{"index":0,"delta":{"content":"streamed\\"}"},"finish_reason":"stop"}]}

                data: [DONE]

                """;

        try (TestAiServer server = TestAiServer.stream(responseBody, requestBody)) {
            AiChatCompletionResult result = client.chatJson(runtimeConfig(server.baseUrl(), true), "system", "user");

            assertThat(result.content()).isEqualTo("{\"answer\":\"streamed\"}");
            assertThat(requestBody.get()).contains("\"stream\":true");
        }
    }

    @Test
    void chatJsonStreamShouldEmitDeltasAndReturnMergedContent() throws Exception {
        String responseBody = """
                data: {"id":"chatcmpl-test","object":"chat.completion.chunk","created":0,"model":"test-model","choices":[{"index":0,"delta":{"role":"assistant","content":"{\\"answer\\":\\"ok"},"finish_reason":null}]}

                data: {"id":"chatcmpl-test","object":"chat.completion.chunk","created":0,"model":"test-model","choices":[{"index":0,"delta":{"content":" "},"finish_reason":null}]}

                data: {"id":"chatcmpl-test","object":"chat.completion.chunk","created":0,"model":"test-model","choices":[{"index":0,"delta":{"content":"now\\"}"},"finish_reason":"stop"}]}

                data: [DONE]

                """;

        try (TestAiServer server = TestAiServer.stream(responseBody)) {
            StringBuilder deltas = new StringBuilder();
            AiChatCompletionResult result = client.chatJsonStream(runtimeConfig(server.baseUrl()), "system", "user", deltas::append);

            assertThat(deltas.toString()).isEqualTo("{\"answer\":\"ok now\"}");
            assertThat(result.content()).isEqualTo("{\"answer\":\"ok now\"}");
        }
    }

    @Test
    void chatJsonShouldPreserveHttpErrorStatus() throws Exception {
        try (TestAiServer server = TestAiServer.json(401, "{\"error\":{\"message\":\"bad key\"}}", new AtomicReference<>())) {
            Throwable thrown = catchThrowable(() -> client.chatJson(runtimeConfig(server.baseUrl(), false), "system", "user"));

            assertThat(thrown).isInstanceOf(AiClientException.class);
            AiClientException ex = (AiClientException) thrown;
            assertThat(ex.getErrorCode()).isEqualTo("401");
            assertThat(ex.getMessage()).contains("bad key");
        }
    }

    @Test
    void chatJsonStreamShouldPreserveHttpErrorStatus() throws Exception {
        try (TestAiServer server = TestAiServer.json(429, "{\"error\":{\"message\":\"too many requests\"}}", new AtomicReference<>())) {
            Throwable thrown = catchThrowable(() -> client.chatJsonStream(runtimeConfig(server.baseUrl()), "system", "user", delta -> {
            }));

            assertThat(thrown).isInstanceOf(AiClientException.class);
            AiClientException ex = (AiClientException) thrown;
            assertThat(ex.getErrorCode()).isEqualTo("429");
            assertThat(ex.getMessage()).contains("too many requests");
        }
    }

    private AiRuntimeConfig runtimeConfig(String baseUrl) {
        return runtimeConfig(baseUrl, true);
    }

    private AiRuntimeConfig runtimeConfig(String baseUrl, boolean streamEnabled) {
        return new AiRuntimeConfig(
                AiConfigScope.PUBLIC,
                baseUrl,
                "test-key",
                "test-model",
                new BigDecimal("0.2"),
                streamEnabled,
                10
        );
    }

    private static final class TestAiServer implements AutoCloseable {

        private final HttpServer server;

        private TestAiServer(String responseBody, String contentType, AtomicReference<String> requestBody) throws IOException {
            this(200, responseBody, contentType, requestBody);
        }

        private TestAiServer(int statusCode, String responseBody, String contentType, AtomicReference<String> requestBody) throws IOException {
            server = HttpServer.create(new InetSocketAddress(0), 0);
            server.createContext("/v1/chat/completions", exchange -> {
                requestBody.set(readRequestBody(exchange));
                writeResponse(exchange, statusCode, responseBody, contentType);
            });
            server.start();
        }

        private static TestAiServer json(String responseBody, AtomicReference<String> requestBody) throws IOException {
            return new TestAiServer(responseBody, "application/json", requestBody);
        }

        private static TestAiServer json(int statusCode, String responseBody, AtomicReference<String> requestBody) throws IOException {
            return new TestAiServer(statusCode, responseBody, "application/json", requestBody);
        }

        private static TestAiServer stream(String responseBody) throws IOException {
            return new TestAiServer(responseBody, "text/event-stream", new AtomicReference<>());
        }

        private static TestAiServer stream(String responseBody, AtomicReference<String> requestBody) throws IOException {
            return new TestAiServer(responseBody, "text/event-stream", requestBody);
        }

        private String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
        }

        private String chatCompletionsUrl() {
            return baseUrl() + "/chat/completions";
        }

        @Override
        public void close() {
            server.stop(0);
        }

        private static String readRequestBody(HttpExchange exchange) throws IOException {
            return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        }

        private static void writeResponse(HttpExchange exchange, int statusCode, String responseBody, String contentType) throws IOException {
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", contentType + "; charset=utf-8");
            exchange.sendResponseHeaders(statusCode, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        }
    }
}
