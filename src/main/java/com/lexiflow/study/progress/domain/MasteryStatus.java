package com.lexiflow.study.progress.domain;

/**
 * 单词掌握状态枚举。
 * <ul>
 *   <li>{@code NEW} - 未学习</li>
 *   <li>{@code LEARNING} - 学习中</li>
 *   <li>{@code REVIEWING} - 复习中</li>
 *   <li>{@code MASTERED} - 已掌握</li>
 *   <li>{@code DIFFICULT} - 困难</li>
 * </ul>
 */
public enum MasteryStatus {
    NEW,
    LEARNING,
    REVIEWING,
    MASTERED,
    DIFFICULT
}
