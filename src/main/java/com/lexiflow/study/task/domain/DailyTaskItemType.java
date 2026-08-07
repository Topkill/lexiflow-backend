package com.lexiflow.study.task.domain;

/**
 * 每日任务项类型枚举。
 *
 * <ul>
 *   <li>{@link #NEW} - 新学单词</li>
 *   <li>{@link #REVIEW} - 复习单词</li>
 *   <li>{@link #EXTRA} - 加练单词（错词巩固）</li>
 * </ul>
 */
public enum DailyTaskItemType {
    /** 新学 */
    NEW,
    /** 复习 */
    REVIEW,
    /** 加练 */
    EXTRA
}
