package com.lexiflow.wordbook.importing.domain;

/**
 * 单词导入任务状态枚举。
 * <p>定义导入任务的执行状态。</p>
 */
public enum WordImportStatus {
    /** 待处理 */
    PENDING,
    /** 执行中 */
    RUNNING,
    /** 全部成功 */
    SUCCESS,
    /** 部分成功 */
    PARTIAL_SUCCESS,
    /** 失败 */
    FAILED
}