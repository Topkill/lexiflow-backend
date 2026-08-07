package com.lexiflow.study.progress.domain;

/**
 * 学习反馈枚举，表示用户对单词的掌握反馈。
 *
 * <ul>
 *   <li>{@link #UNKNOWN} - 不认识，用户表示尚未掌握该单词</li>
 *   <li>{@link #KNOWN} - 认识，用户表示已掌握该单词</li>
 * </ul>
 */
public enum StudyFeedback {
    /** 不认识 */
    UNKNOWN,
    /** 认识 */
    KNOWN
}
