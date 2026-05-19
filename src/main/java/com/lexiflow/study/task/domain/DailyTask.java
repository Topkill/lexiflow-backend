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

@Getter
@Setter
@TableName("daily_task")
public class DailyTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private Long planId;
    private LocalDate taskDate;
    private Integer groupNo;
    private DailyTaskStatus status;
    private Integer newCount;
    private Integer reviewCount;
    private Integer extraCount;
    private Integer doneCount;
    private Integer skippedCount;
    private LocalDateTime completedAt;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
}
