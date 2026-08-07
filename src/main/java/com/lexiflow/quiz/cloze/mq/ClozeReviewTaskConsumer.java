package com.lexiflow.quiz.cloze.mq;

import com.lexiflow.infra.rabbitmq.RabbitMqNames;
import com.lexiflow.quiz.cloze.service.ClozeAttemptAiReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * 完形填空 AI 评阅任务消费者。
 * <p>监听 {@link RabbitMqNames#CLOZE_REVIEW_QUEUE} 队列，将评阅消息委派给
 * {@link ClozeAttemptAiReviewService#processReviewTask} 执行。
 * 支持 RabbitMQ 重投递恢复处理。</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ClozeReviewTaskConsumer {

    private final ClozeAttemptAiReviewService clozeAttemptAiReviewService;

    /**
     * 处理 AI 评阅 MQ 消息。
     *
     * @param message    评阅任务消息，包含异步任务 ID
     * @param redelivered 是否为 RabbitMQ 重投递消息
     */
    @RabbitListener(queues = RabbitMqNames.CLOZE_REVIEW_QUEUE)
    public void handle(ClozeReviewTaskMessage message, @Header(name = AmqpHeaders.REDELIVERED, required = false) Boolean redelivered) {
        if (message == null || message.taskId() == null) {
            log.warn("忽略空的 AI 评阅 MQ 消息");
            return;
        }
        try {
            clozeAttemptAiReviewService.processReviewTask(message.taskId(), Boolean.TRUE.equals(redelivered));
        } catch (RuntimeException ex) {
            log.error("AI 评阅 MQ 消息处理异常，taskId={}", message.taskId(), ex);
            throw ex;
        }
    }
}
