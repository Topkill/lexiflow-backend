package com.lexiflow.ai.core.client;

/**
 * AI 流式响应增量处理器
 * <p>
 * 函数式接口，用于处理 AI 流式调用中每个增量文本片段。
 * </p>
 */
@FunctionalInterface
public interface AiStreamDeltaHandler {

    /**
     * 处理一个增量文本片段
     *
     * @param delta 增量文本内容
     * @throws Exception 处理失败时抛出异常
     */
    void onDelta(String delta) throws Exception;
}
