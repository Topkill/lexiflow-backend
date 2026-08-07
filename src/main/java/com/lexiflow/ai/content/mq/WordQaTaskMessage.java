package com.lexiflow.ai.content.mq;

/**
 * 单词问答任务 MQ 消息体
 * <p>
 * 封装异步任务的 ID，用于在 RabbitMQ 中传递单词问答任务。
 * </p>
 *
 * @param taskId 异步任务 ID
 */
public record WordQaTaskMessage(Long taskId) {
}
