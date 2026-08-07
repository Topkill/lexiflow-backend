package com.lexiflow.wordbook.importing.mq;

import com.lexiflow.infra.rabbitmq.RabbitMqNames;
import com.lexiflow.wordbook.importing.service.WordImportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * 单词导入任务消费者。
 * <p>监听 RabbitMQ 队列，处理单词导入任务消息。</p>
 *
 * @see WordImportService
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WordImportTaskConsumer {

    private final WordImportService wordImportService;

    @RabbitListener(queues = RabbitMqNames.WORD_IMPORT_QUEUE)
    public void handle(WordImportTaskMessage message, @Header(name = AmqpHeaders.REDELIVERED, required = false) Boolean redelivered) {
        if (message == null || message.importTaskId() == null) {
            log.warn("忽略空的词库导入 MQ 消息");
            return;
        }
        try {
            wordImportService.processImportTask(message.importTaskId(), Boolean.TRUE.equals(redelivered));
        } catch (RuntimeException ex) {
            log.error("词库导入 MQ 消息处理异常，importTaskId={}", message.importTaskId(), ex);
            throw ex;
        }
    }
}
