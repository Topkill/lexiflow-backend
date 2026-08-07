package com.lexiflow.wordbook.importing.mq;

import com.lexiflow.infra.rabbitmq.RabbitMqNames;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * 单词导入任务发布者。
 * <p>将导入任务消息发送到 RabbitMQ 队列，触发异步处理。</p>
 */
@Component
@RequiredArgsConstructor
public class WordImportTaskPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publish(Long importTaskId) {
        rabbitTemplate.convertAndSend(
                RabbitMqNames.WORD_IMPORT_EXCHANGE,
                RabbitMqNames.WORD_IMPORT_ROUTING_KEY,
                new WordImportTaskMessage(importTaskId)
        );
    }
}
