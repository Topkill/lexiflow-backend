package com.lexiflow.study.task.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 每日任务项实体。
 * <p>表示每日任务中的一个具体单词学习项，记录单词ID、类型、状态和用户反馈。</p>
 */
@Getter
@Setter
@TableName("daily_task_item")
public class DailyTaskItem {

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属每日任务 ID */
    private Long dailyTaskId;
    /** 用户 ID */
    private Long userId;
    /** 关联的学习计划 ID */
    private Long planId;
    /** 词书 ID */
    private Long wordbookId;
    /** 单词 ID */
    private Long wordId;
    /** 任务项类型（新学/复习/加练） */
    private DailyTaskItemType itemType;
    /** 任务项状态 */
    private DailyTaskItemStatus status;
    /** 排序序号（控制单词展示顺序） */
    private Integer sequenceNo;
    /** 用户反馈（UNKNOWN/KKNOWN） */
    private String feedback;
    /** 完成时间 */
    private LocalDateTime doneAt;
    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    /** 逻辑删除标志 */
    @TableLogic
    private Integer deleted;
}
