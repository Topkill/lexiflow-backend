package com.lexiflow.ai.content.mq;

import com.lexiflow.infra.rabbitmq.RabbitMqNames;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * 单词问答任务 MQ 发布者
 * <p>
 * 将单词问答异步任务消息发布到 RabbitMQ 队列，供消费者异步处理。
 * </p>
 */
@Component
@RequiredArgsConstructor
public class WordQaTaskPublisher {

    private final RabbitTemplate rabbitTemplate;

    /**
     * 发布单词问答任务消息到 RabbitMQ
     *
     * @param taskId 异步任务 ID
     */
    public void publish(Long taskId) {
        rabbitTemplate.convertAndSend(
                RabbitMqNames.WORD_QA_EXCHANGE,
                RabbitMqNames.WORD_QA_ROUTING_KEY,
                new WordQaTaskMessage(taskId)
        );
    }
}
