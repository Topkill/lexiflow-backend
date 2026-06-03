package com.lexiflow.quiz.cloze.mq;

import com.lexiflow.infra.rabbitmq.RabbitMqNames;
import com.lexiflow.quiz.cloze.service.ClozeAttemptAiReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ClozeReviewTaskConsumer {

    private final ClozeAttemptAiReviewService clozeAttemptAiReviewService;

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
