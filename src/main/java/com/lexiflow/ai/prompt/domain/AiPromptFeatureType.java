package com.lexiflow.ai.prompt.domain;

/**
 * AI 提示词功能类型枚举
 * <p>
 * 定义系统支持 AI 提示词的功能类型，每个类型包含对应的中文标签。
 * </p>
 */
public enum AiPromptFeatureType {
    WORD_QA("AI 问答"),
    CLOZE_QUIZ("AI 完形填空"),
    CLOZE_REVIEW("AI 评阅");

    private final String label;

    AiPromptFeatureType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
