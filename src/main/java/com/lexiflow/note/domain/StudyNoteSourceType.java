package com.lexiflow.note.domain;

/**
 * 学习笔记来源类型枚举。
 * <p>定义笔记的来源方式。</p>
 */
public enum StudyNoteSourceType {
    /** 普通笔记 */
    NORMAL,
    /** AI 问答摘录 */
    WORD_QA,
    /** AI 评阅摘录 */
    CLOZE_REVIEW
}
