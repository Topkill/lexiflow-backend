package com.lexiflow.study.task.domain;

/**
 * 每日任务状态枚举。
 *
 * <ul>
 *   <li>{@link #PENDING} - 进行中</li>
 *   <li>{@link #DONE} - 已完成</li>
 *   <li>{@link #EXPIRED} - 已过期</li>
 * </ul>
 */
public enum DailyTaskStatus {
    /** 进行中 */
    PENDING,
    /** 已完成 */
    DONE,
    /** 已过期 */
    EXPIRED
}
