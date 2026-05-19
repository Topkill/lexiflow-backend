package com.lexiflow.report.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("study_report")
public class StudyReport {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private Long planId;
    private Long wordbookId;
    private Long dailyTaskId;
    private Long asyncTaskId;
    private LocalDate reportDate;
    private Integer newWordsCount;
    private Integer reviewWordsCount;
    private BigDecimal quizAccuracy;
    private String wrongWordIds;
    private String summaryJson;
    private String markdownContent;
    private String modelName;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
}
