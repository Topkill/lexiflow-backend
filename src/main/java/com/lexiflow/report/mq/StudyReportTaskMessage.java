package com.lexiflow.report.mq;

/**
 * 学习报告生成任务 MQ 消息体。
 *
 * @param taskId 异步任务 ID
 */
public record StudyReportTaskMessage(Long taskId) {
}
