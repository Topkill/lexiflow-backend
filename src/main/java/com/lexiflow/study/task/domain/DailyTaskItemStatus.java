package com.lexiflow.study.task.domain;

/**
 * 每日任务项状态枚举。
 *
 * <ul>
 *   <li>{@link #PENDING} - 待完成</li>
 *   <li>{@link #DONE} - 已完成</li>
 *   <li>{@link #SKIPPED} - 已跳过</li>
 * </ul>
 */
public enum DailyTaskItemStatus {
    /** 待完成 */
    PENDING,
    /** 已完成 */
    DONE,
    /** 已跳过 */
    SKIPPED
}
