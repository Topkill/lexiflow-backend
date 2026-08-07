package com.lexiflow.wordbook.importing.mq;

/**
 * 单词导入任务消息 DTO。
 * <p>用于 RabbitMQ 消息传递，包含导入任务ID。</p>
 *
 * @param importTaskId 导入任务ID
 */
public record WordImportTaskMessage(Long importTaskId) {
}
