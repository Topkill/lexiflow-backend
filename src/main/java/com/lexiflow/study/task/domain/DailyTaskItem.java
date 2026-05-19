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

@Getter
@Setter
@TableName("daily_task_item")
public class DailyTaskItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long dailyTaskId;
    private Long userId;
    private Long planId;
    private Long wordbookId;
    private Long wordId;
    private DailyTaskItemType itemType;
    private DailyTaskItemStatus status;
    private Integer sequenceNo;
    private String feedback;
    private LocalDateTime doneAt;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
}
