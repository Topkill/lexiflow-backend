package com.lexiflow.quiz.cloze.mq;

import com.lexiflow.infra.rabbitmq.RabbitMqNames;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * 完形填空 AI 评阅任务消息发布者。
 * <p>将评阅任务消息发送至 RabbitMQ 的评阅交换机，由 {@link ClozeReviewTaskConsumer} 消费处理。</p>
 */
@Component
@RequiredArgsConstructor
public class ClozeReviewTaskPublisher {

    private final RabbitTemplate rabbitTemplate;

    /**
     * 发布 AI 评阅任务消息。
     *
     * @param taskId 异步评阅任务 ID
     */
    public void publish(Long taskId) {
        rabbitTemplate.convertAndSend(
                RabbitMqNames.CLOZE_REVIEW_EXCHANGE,
                RabbitMqNames.CLOZE_REVIEW_ROUTING_KEY,
                new ClozeReviewTaskMessage(taskId)
        );
    }
}
