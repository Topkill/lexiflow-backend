package com.lexiflow.study.task.domain;

/**
 * 每日任务类型枚举。
 *
 * <ul>
 *   <li>{@link #DAILY} - 每日学习任务（包含新学、复习、加练）</li>
 *   <li>{@link #WRONG_WORD_PRACTICE} - 错词练习（单独的错词巩固任务）</li>
 * </ul>
 */
public enum DailyTaskType {
    /** 每日学习任务 */
    DAILY,
    /** 错词练习 */
    WRONG_WORD_PRACTICE
}
