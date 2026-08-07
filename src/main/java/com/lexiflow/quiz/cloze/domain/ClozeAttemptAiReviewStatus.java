package com.lexiflow.quiz.cloze.domain;

/**
 * 完形填空 AI 评阅状态枚举。
 * <p>定义 AI 评阅任务的执行状态。</p>
 */
public enum ClozeAttemptAiReviewStatus {
    /** 执行中 */
    RUNNING,
    /** 已完成 */
    DONE,
    /** 失败 */
    FAILED
}
