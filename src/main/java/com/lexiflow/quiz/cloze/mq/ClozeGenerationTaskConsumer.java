package com.lexiflow.quiz.cloze.mq;

import com.lexiflow.infra.rabbitmq.RabbitMqNames;
import com.lexiflow.quiz.cloze.service.ClozeQuizService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ClozeGenerationTaskConsumer {

    private final ClozeQuizService clozeQuizService;

    @RabbitListener(queues = RabbitMqNames.CLOZE_GENERATION_QUEUE)
    public void handle(ClozeGenerationTaskMessage message, @Header(name = AmqpHeaders.REDELIVERED, required = false) Boolean redelivered) {
        if (message == null || message.taskId() == null) {
            log.warn("忽略空的完形填空生成 MQ 消息");
            return;
        }
        try {
            clozeQuizService.processClozeTask(message.taskId(), Boolean.TRUE.equals(redelivered));
        } catch (RuntimeException ex) {
            log.error("完形填空生成 MQ 消息处理异常，taskId={}", message.taskId(), ex);
            throw ex;
        }
    }
}
