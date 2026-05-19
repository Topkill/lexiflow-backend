package com.lexiflow.study.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("study_plan")
public class StudyPlan {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private Long wordbookId;
    private String name;
    private Integer dailyNewWords;
    private LocalDate startDate;
    private LocalDate expectedFinishDate;
    private LocalDate actualFinishDate;
    private StudyPlanStatus status;
    private Integer totalWords;
    private Integer learnedCount;
    private Integer reviewedCount;
    private Integer masteredCount;
    private Integer currentSequenceNo;
    private Boolean isPrimary;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
    @Version
    private Integer version;
}
