package com.lexiflow.study.domain;

/**
 * 学习计划状态枚举。
 * <ul>
 *   <li>{@code ACTIVE} - 进行中</li>
 *   <li>{@code PAUSED} - 已暂停</li>
 *   <li>{@code COMPLETED} - 已完成</li>
 *   <li>{@code ENDED} - 已结束</li>
 * </ul>
 */
public enum StudyPlanStatus {
    ACTIVE,
    PAUSED,
    COMPLETED,
    ENDED
}
