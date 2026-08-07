package com.lexiflow.report.mq;

import com.lexiflow.infra.rabbitmq.RabbitMqNames;
import com.lexiflow.report.service.StudyReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * 学习报告生成任务消费者。
 * <p>监听 {@link RabbitMqNames#STUDY_REPORT_QUEUE} 队列，将消息委派给
 * {@link StudyReportService#processReportTask} 执行。</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StudyReportTaskConsumer {

    private final StudyReportService studyReportService;

    /**
     * 处理学习报告生成 MQ 消息。
     *
     * @param message     报告任务消息，包含异步任务 ID
     * @param redelivered 是否为 RabbitMQ 重投递消息
     */
    @RabbitListener(queues = RabbitMqNames.STUDY_REPORT_QUEUE)
    public void handle(StudyReportTaskMessage message, @Header(name = AmqpHeaders.REDELIVERED, required = false) Boolean redelivered) {
        if (message == null || message.taskId() == null) {
            log.warn("忽略空的学习报告 MQ 消息");
            return;
        }
        try {
            studyReportService.processReportTask(message.taskId(), Boolean.TRUE.equals(redelivered));
        } catch (RuntimeException ex) {
            log.error("学习报告 MQ 消息处理异常，taskId={}", message.taskId(), ex);
            throw ex;
        }
    }
}
