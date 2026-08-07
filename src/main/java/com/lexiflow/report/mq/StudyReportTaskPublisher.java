package com.lexiflow.report.mq;

import com.lexiflow.infra.rabbitmq.RabbitMqNames;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * 学习报告生成任务消息发布者。
 * <p>将报告任务消息发送至 RabbitMQ 的报告交换机，由 {@link StudyReportTaskConsumer} 消费处理。</p>
 */
@Component
@RequiredArgsConstructor
public class StudyReportTaskPublisher {

    private final RabbitTemplate rabbitTemplate;

    /**
     * 发布学习报告生成任务消息。
     *
     * @param taskId 异步任务 ID
     */
    public void publish(Long taskId) {
        rabbitTemplate.convertAndSend(
                RabbitMqNames.STUDY_REPORT_EXCHANGE,
                RabbitMqNames.STUDY_REPORT_ROUTING_KEY,
                new StudyReportTaskMessage(taskId)
        );
    }
}
