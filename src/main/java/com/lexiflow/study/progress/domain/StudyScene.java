package com.lexiflow.study.progress.domain;

/**
 * 学习场景枚举，表示单词学习发生的场景类型。
 *
 * <ul>
 *   <li>{@link #NEW} - 新学，首次学习该单词</li>
 *   <li>{@link #REVIEW} - 复习，按间隔重复计划复习</li>
 *   <li>{@link #EXTRA} - 加练，错词巩固练习</li>
 *   <li>{@link #QUIZ} - 测验，填空测验场景</li>
 * </ul>
 */
public enum StudyScene {
    /** 新学 */
    NEW,
    /** 复习 */
    REVIEW,
    /** 加练 */
    EXTRA,
    /** 测验 */
    QUIZ
}
