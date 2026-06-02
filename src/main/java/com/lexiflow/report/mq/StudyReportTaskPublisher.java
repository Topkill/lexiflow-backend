package com.lexiflow.report.mq;

import com.lexiflow.infra.rabbitmq.RabbitMqNames;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StudyReportTaskPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publish(Long taskId) {
        rabbitTemplate.convertAndSend(
                RabbitMqNames.STUDY_REPORT_EXCHANGE,
                RabbitMqNames.STUDY_REPORT_ROUTING_KEY,
                new StudyReportTaskMessage(taskId)
        );
    }
}
