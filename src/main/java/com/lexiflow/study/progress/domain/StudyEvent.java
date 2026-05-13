package com.lexiflow.study.progress.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("study_event")
public class StudyEvent {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;
    private Long planId;
    private Long wordbookId;
    private Long wordId;
    private Long dailyTaskId;
    private Long dailyTaskItemId;
    private StudyScene scene;
    private StudyFeedback feedback;
    private Integer qualityScore;
    private Boolean isCorrect;
    private Integer durationSeconds;
    private Long sourceRefId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
