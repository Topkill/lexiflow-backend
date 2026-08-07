package com.lexiflow.quiz.cloze.mq;

/**
 * 完形填空 AI 评阅任务 MQ 消息体。
 *
 * @param taskId 异步评阅任务 ID
 */
public record ClozeReviewTaskMessage(Long taskId) {
}
