package com.lexiflow.quiz.cloze.mq;

import com.lexiflow.infra.rabbitmq.RabbitMqNames;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * 完形填空生成任务消息发布者。
 * <p>向 RabbitMQ 发送完形填空生成任务消息，触发异步生成流程。</p>
 */
@Component
@RequiredArgsConstructor
public class ClozeGenerationTaskPublisher {

    private final RabbitTemplate rabbitTemplate;

    /** 发布完形填空生成任务消息。 */
    public void publish(Long taskId) {
        rabbitTemplate.convertAndSend(
                RabbitMqNames.CLOZE_GENERATION_EXCHANGE,
                RabbitMqNames.CLOZE_GENERATION_ROUTING_KEY,
                new ClozeGenerationTaskMessage(taskId)
        );
    }
}
