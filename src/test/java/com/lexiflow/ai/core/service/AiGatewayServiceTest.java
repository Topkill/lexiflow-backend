package com.lexiflow.ai.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lexiflow.ai.content.domain.AiCallLog;
import com.lexiflow.ai.content.domain.AiCallStatus;
import com.lexiflow.ai.content.domain.AiContentType;
import com.lexiflow.ai.content.mapper.AiCallLogMapper;
import com.lexiflow.ai.core.client.AiClientException;
import com.lexiflow.ai.core.client.SpringAiChatClient;
import com.lexiflow.ai.core.dto.AiChatCompletionResult;
import com.lexiflow.ai.core.dto.AiConfigScope;
import com.lexiflow.ai.core.dto.AiPrompt;
import com.lexiflow.ai.core.dto.AiRuntimeConfig;
import com.lexiflow.common.error.ErrorCode;
import com.lexiflow.common.exception.BizException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AiGatewayServiceTest {

    private final AiConfigResolver configResolver = mock(AiConfigResolver.class);
    private final AiQuotaService quotaService = mock(AiQuotaService.class);
    private final SpringAiChatClient chatClient = mock(SpringAiChatClient.class);
    private final AiCallLogMapper logMapper = mock(AiCallLogMapper.class);
    private final AiGatewayService service = new AiGatewayService(configResolver, quotaService, chatClient, logMapper);

    @Test
    void generateJsonShouldKeepResultAndSuccessLogContract() {
        AiRuntimeConfig config = runtimeConfig();
        AiPrompt prompt = prompt();
        AiChatCompletionResult result = new AiChatCompletionResult("{\"ok\":true}", 3, 4, 7);
        when(configResolver.resolve(9L)).thenReturn(config);
        when(chatClient.chatJson(config, prompt.systemPrompt(), prompt.userPrompt())).thenReturn(result);

        AiChatCompletionResult actual = service.generateJson(9L, AiContentType.WORD_QA, prompt);

        assertThat(actual).isSameAs(result);
        verify(quotaService).checkQuota(9L, config);
        ArgumentCaptor<AiCallLog> captor = ArgumentCaptor.forClass(AiCallLog.class);
        verify(logMapper).insert(captor.capture());
        AiCallLog log = captor.getValue();
        assertThat(log.getStatus()).isEqualTo(AiCallStatus.SUCCESS);
        assertThat(log.getPromptTokens()).isEqualTo(3);
        assertThat(log.getCompletionTokens()).isEqualTo(4);
        assertThat(log.getTotalTokens()).isEqualTo(7);
        assertThat(log.getErrorCode()).isNull();
    }

    @Test
    void generateJsonShouldMapClientExceptionToAiCallFailedAndFailedLog() {
        AiRuntimeConfig config = runtimeConfig();
        AiPrompt prompt = prompt();
        when(configResolver.resolve(9L)).thenReturn(config);
        doThrow(new AiClientException("401", "bad key"))
                .when(chatClient).chatJson(config, prompt.systemPrompt(), prompt.userPrompt());

        assertThatThrownBy(() -> service.generateJson(9L, AiContentType.WORD_QA, prompt))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.AI_CALL_FAILED);

        ArgumentCaptor<AiCallLog> captor = ArgumentCaptor.forClass(AiCallLog.class);
        verify(logMapper).insert(captor.capture());
        AiCallLog log = captor.getValue();
        assertThat(log.getStatus()).isEqualTo(AiCallStatus.FAILED);
        assertThat(log.getErrorCode()).isEqualTo("401");
        assertThat(log.getErrorMessage()).isEqualTo("bad key");
    }

    private AiRuntimeConfig runtimeConfig() {
        return new AiRuntimeConfig(
                AiConfigScope.PUBLIC,
                "http://localhost:8115/v1",
                "test-key",
                "test-model",
                new BigDecimal("0.2"),
                true,
                10
        );
    }

    private AiPrompt prompt() {
        return new AiPrompt("system", "user", "hash", "WORD_QA", 1L, "模板");
    }
}
