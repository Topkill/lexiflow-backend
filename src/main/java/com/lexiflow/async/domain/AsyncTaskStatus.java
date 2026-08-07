package com.lexiflow.async.domain;

/**
 * 异步任务状态枚举
 * <p>
 * 表示异步任务的生命周期状态：等待执行、运行中、成功、失败。
 * </p>
 */
public enum AsyncTaskStatus {
    /** 等待执行 */
    PENDING,
    /** 运行中 */
    RUNNING,
    /** 执行成功 */
    SUCCESS,
    /** 执行失败 */
    FAILED
}