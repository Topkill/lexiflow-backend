package com.lexiflow.quiz.cloze.mq;

import com.lexiflow.infra.rabbitmq.RabbitMqNames;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ClozeReviewTaskPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publish(Long taskId) {
        rabbitTemplate.convertAndSend(
                RabbitMqNames.CLOZE_REVIEW_EXCHANGE,
                RabbitMqNames.CLOZE_REVIEW_ROUTING_KEY,
                new ClozeReviewTaskMessage(taskId)
        );
    }
}
