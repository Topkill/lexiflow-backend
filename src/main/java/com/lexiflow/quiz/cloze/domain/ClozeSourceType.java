package com.lexiflow.quiz.cloze.domain;

/**
 * 完形填空来源类型枚举。
 * <p>定义完形填空题目的单词来源方式。</p>
 */
public enum ClozeSourceType {
    /** 今日新词 */
    TODAY_NEW,
    /** 错词 */
    WRONG_WORDS,
    /** 混合 */
    MIXED,
    /** 已完成组 */
    COMPLETED_GROUP
}
