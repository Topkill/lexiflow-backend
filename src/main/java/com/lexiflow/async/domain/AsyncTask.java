package com.lexiflow.async.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 异步任务实体
 * <p>
 * 记录异步任务的完整生命周期信息，包括任务类型、状态、进度、请求参数、执行结果和错误信息等。
 * </p>
 */
@Getter
@Setter
@TableName("async_task")
public class AsyncTask {

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 任务所属用户 ID */
    private Long userId;
    /** 任务类型 */
    private AsyncTaskType taskType;
    /** 任务状态 */
    private AsyncTaskStatus status;
    /** 任务进度（0-100） */
    private Integer progress;
    /** 状态消息 */
    private String message;
    /** 任务请求参数 JSON */
    private String requestJson;
    /** 任务结果 ID */
    private Long resultId;
    /** 错误码 */
    private String errorCode;
    /** 错误信息 */
    private String errorMessage;
    /** 任务开始时间 */
    private LocalDateTime startedAt;
    /** 任务完成时间 */
    private LocalDateTime finishedAt;
    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
