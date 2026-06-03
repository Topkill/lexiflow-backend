package com.lexiflow.ai.content.mq;

import com.lexiflow.infra.rabbitmq.RabbitMqNames;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WordQaTaskPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publish(Long taskId) {
        rabbitTemplate.convertAndSend(
                RabbitMqNames.WORD_QA_EXCHANGE,
                RabbitMqNames.WORD_QA_ROUTING_KEY,
                new WordQaTaskMessage(taskId)
        );
    }
}
