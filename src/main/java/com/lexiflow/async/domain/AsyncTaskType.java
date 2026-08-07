package com.lexiflow.async.domain;

/**
 * 异步任务类型枚举
 * <p>
 * 定义系统支持的异步任务类型，包括 AI 单词问答、AI 完形填空、AI 评阅和 AI 报告。
 * </p>
 */
public enum AsyncTaskType {
    /** AI 单词问答 */
    AI_WORD_QA,
    /** AI 完形填空 */
    AI_CLOZE,
    /** AI 填空评阅 */
    AI_CLOZE_REVIEW,
    /** AI 学习报告 */
    AI_REPORT
}
