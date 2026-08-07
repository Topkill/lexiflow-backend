package com.lexiflow.ai.content.mq;

import com.lexiflow.ai.content.service.WordAiContentService;
import com.lexiflow.infra.rabbitmq.RabbitMqNames;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * 单词问答任务 MQ 消费者
 * <p>
 * 监听 RabbitMQ 单词问答队列，消费异步任务消息并调用
 * {@link WordAiContentService#processWordQaTask} 执行实际的 AI 问答生成逻辑。
 * </p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WordQaTaskConsumer {

    private final WordAiContentService wordAiContentService;

    /**
     * 处理单词问答异步任务消息
     *
     * @param message 任务消息，包含异步任务 ID
     * @param redelivered 是否为 RabbitMQ 重投的消息
     */
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
