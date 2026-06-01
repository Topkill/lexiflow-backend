package com.lexiflow.ai.core.service;

import com.lexiflow.ai.content.domain.AiCallLog;
import com.lexiflow.ai.content.domain.AiCallStatus;
import com.lexiflow.ai.content.domain.AiContentType;
import com.lexiflow.ai.content.mapper.AiCallLogMapper;
import com.lexiflow.ai.core.client.AiClientException;
import com.lexiflow.ai.core.client.AiStreamDeltaHandler;
import com.lexiflow.ai.core.client.SpringAiChatClient;
import com.lexiflow.ai.core.dto.AiChatCompletionResult;
import com.lexiflow.ai.core.dto.AiPrompt;
import com.lexiflow.ai.core.dto.AiRuntimeConfig;
import com.lexiflow.common.error.ErrorCode;
import com.lexiflow.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AiGatewayService {

    private final AiConfigResolver aiConfigResolver;
    private final AiQuotaService aiQuotaService;
    private final SpringAiChatClient springAiChatClient;
    private final AiCallLogMapper aiCallLogMapper;

    public AiChatCompletionResult generateJson(Long userId, AiContentType contentType, AiPrompt prompt) {
        AiRuntimeConfig config = aiConfigResolver.resolve(userId);
        aiQuotaService.checkQuota(userId, config);
        long startNanos = System.nanoTime();
        try {
            AiChatCompletionResult result = springAiChatClient.chatJson(config, prompt.systemPrompt(), prompt.userPrompt());
            saveLog(userId, contentType, prompt, config, result, latencyMs(startNanos), null, null);
            return result;
        } catch (AiClientException ex) {
            saveLog(userId, contentType, prompt, config, null, latencyMs(startNanos), ex.getErrorCode(), ex.getMessage());
            throw new BizException(ErrorCode.AI_CALL_FAILED, "AI 调用失败，请稍后重试");
        }
    }

    public AiChatCompletionResult generateJsonStream(Long userId, AiContentType contentType, AiPrompt prompt, AiStreamDeltaHandler deltaHandler) {
        AiRuntimeConfig config = aiConfigResolver.resolve(userId);
        aiQuotaService.checkQuota(userId, config);
        long startNanos = System.nanoTime();
        try {
            AiChatCompletionResult result = springAiChatClient.chatJsonStream(config, prompt.systemPrompt(), prompt.userPrompt(), deltaHandler);
            saveLog(userId, contentType, prompt, config, result, latencyMs(startNanos), null, null);
            return result;
        } catch (AiClientException ex) {
            saveLog(userId, contentType, prompt, config, null, latencyMs(startNanos), ex.getErrorCode(), ex.getMessage());
            throw new BizException(ErrorCode.AI_CALL_FAILED, "AI 调用失败，请稍后重试");
        }
    }

    private void saveLog(
            Long userId,
            AiContentType contentType,
            AiPrompt prompt,
            AiRuntimeConfig config,
            AiChatCompletionResult result,
            int latencyMs,
            String errorCode,
            String errorMessage
    ) {
        AiCallLog log = new AiCallLog();
        log.setUserId(userId);
        log.setConfigScope(config.scope());
        log.setContentType(contentType);
        log.setModelName(config.modelName());
        log.setApiBaseUrl(config.apiBaseUrl());
        log.setRequestHash(prompt == null ? null : prompt.requestHash());
        log.setPromptFeatureType(prompt == null ? null : prompt.promptFeatureType());
        log.setPromptTemplateId(prompt == null ? null : prompt.promptTemplateId());
        log.setPromptTemplateName(prompt == null ? null : prompt.promptTemplateName());
        log.setStatus(errorCode == null ? AiCallStatus.SUCCESS : AiCallStatus.FAILED);
        log.setPromptTokens(result == null ? 0 : result.promptTokens());
        log.setCompletionTokens(result == null ? 0 : result.completionTokens());
        log.setTotalTokens(result == null ? 0 : result.totalTokens());
        log.setLatencyMs(latencyMs);
        log.setErrorCode(errorCode);
        log.setErrorMessage(abbreviate(errorMessage));
        aiCallLogMapper.insert(log);
    }

    private int latencyMs(long startNanos) {
        long elapsed = (System.nanoTime() - startNanos) / 1_000_000L;
        return elapsed > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) elapsed;
    }

    private String abbreviate(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        return message.length() > 1024 ? message.substring(0, 1024) : message;
    }
}
