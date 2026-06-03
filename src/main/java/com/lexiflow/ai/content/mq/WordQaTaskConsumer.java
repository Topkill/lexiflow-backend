package com.lexiflow.ai.content.mq;

import com.lexiflow.ai.content.service.WordAiContentService;
import com.lexiflow.infra.rabbitmq.RabbitMqNames;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class WordQaTaskConsumer {

    private final WordAiContentService wordAiContentService;

    @RabbitListener(queues = RabbitMqNames.WORD_QA_QUEUE)
    public void handle(WordQaTaskMessage message, @Header(name = AmqpHeaders.REDELIVERED, required = false) Boolean redelivered) {
        if (message == null || message.taskId() == null) {
            log.warn("忽略空的 AI 问答 MQ 消息");
            return;
        }
        try {
            wordAiContentService.processWordQaTask(message.taskId(), Boolean.TRUE.equals(redelivered));
        } catch (RuntimeException ex) {
            log.error("AI 问答 MQ 消息处理异常，taskId={}", message.taskId(), ex);
            throw ex;
        }
    }
}
