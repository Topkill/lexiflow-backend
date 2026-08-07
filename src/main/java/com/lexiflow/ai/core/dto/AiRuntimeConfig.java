package com.lexiflow.ai.core.dto;

import java.math.BigDecimal;

/**
 * AI 运行时配置
 * <p>
 * 封装 AI 调用时所需的运行时配置信息，包括 API 地址、密钥、模型参数等。
 * 由 {@link com.lexiflow.ai.core.service.AiConfigResolver} 解析生成。
 * </p>
 *
 * @param scope 配置来源（公共/私有）
 * @param apiBaseUrl API 基础地址
 * @param apiKey API 密钥（已解密）
 * @param modelName 模型名称
 * @param temperature 温度参数
 * @param streamEnabled 是否启用流式输出
 * @param dailyQuotaPerUser 每用户每日公共调用配额（仅公共配置有效）
 */
public record AiRuntimeConfig(
        AiConfigScope scope,
        String apiBaseUrl,
        String apiKey,
        String modelName,
        BigDecimal temperature,
        Boolean streamEnabled,
        Integer dailyQuotaPerUser
) {
    /**
     * 判断是否启用流式输出
     *
     * @return 如果 streamEnabled 为 true 则返回 true
     */
    public boolean useStream() {
        return Boolean.TRUE.equals(streamEnabled);
    }
}
