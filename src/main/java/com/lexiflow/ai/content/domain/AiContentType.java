package com.lexiflow.ai.content.domain;

/**
 * AI 内容类型枚举
 * <p>
 * 定义系统支持的 AI 生成内容类型，包括单词问答、填空、填空复习和学习报告。
 * </p>
 */
public enum AiContentType {
    /** 单词问答 */
    WORD_QA,
    /** 填空题 */
    CLOZE,
    /** 填空题复习 */
    CLOZE_REVIEW,
    /** 学习报告 */
    REPORT
}
