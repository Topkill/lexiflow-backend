package com.lexiflow.study.task.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 每日任务实体。
 * <p>记录用户每天的学习任务，包括新学、复习、加练的单词数量及完成进度。
 * 同一用户同一天可以有多个任务组（通过 groupNo 区分）。</p>
 */
@Getter
@Setter
@TableName("daily_task")
public class DailyTask {

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户 ID */
    private Long userId;
    /** 关联的学习计划 ID */
    private Long planId;
    /** 任务日期 */
    private LocalDate taskDate;
    /** 任务组号（同一天可有多组任务） */
    private Integer groupNo;
    /** 任务类型 */
    private DailyTaskType taskType;
    /** 任务状态 */
    private DailyTaskStatus status;
    /** 新学单词数 */
    private Integer newCount;
    /** 复习单词数 */
    private Integer reviewCount;
    /** 加练单词数 */
    private Integer extraCount;
    /** 已完成数 */
    private Integer doneCount;
    /** 已跳过数 */
    private Integer skippedCount;
    /** 任务完成时间 */
    private LocalDateTime completedAt;
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
