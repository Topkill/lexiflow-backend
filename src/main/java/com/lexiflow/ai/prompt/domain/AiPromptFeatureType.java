package com.lexiflow.ai.prompt.domain;

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
