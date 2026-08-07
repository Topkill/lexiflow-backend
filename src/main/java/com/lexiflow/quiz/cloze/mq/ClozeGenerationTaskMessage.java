package com.lexiflow.quiz.cloze.mq;

/**
 * 完形填空生成任务消息 DTO。
 * <p>用于 RabbitMQ 消息传递，包含异步任务ID。</p>
 *
 * @param taskId 异步任务ID
 */
public record ClozeGenerationTaskMessage(Long taskId) {
}
