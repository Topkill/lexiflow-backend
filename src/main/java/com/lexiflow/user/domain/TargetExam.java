package com.lexiflow.user.domain;

/**
 * 目标考试类型枚举。
 *
 * <p>用户可选择自己的目标考试类型，系统据此调整学习内容和难度。</p>
 */
public enum TargetExam {
    /** 大学英语四级考试 */
    CET4,
    /** 大学英语六级考试 */
    CET6,
    /** 考研英语 */
    POSTGRADUATE
}
