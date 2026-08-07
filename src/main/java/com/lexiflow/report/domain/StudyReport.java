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

/**
 * 学习报告实体。
 * <p>存储 AI 生成的每日学习报告，包含学习统计、结构化总结和 Markdown 内容。</p>
 */
@Getter
@Setter
@TableName("study_report")
public class StudyReport {

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户 ID */
    private Long userId;
    /** 学习计划 ID */
    private Long planId;
    /** 词书 ID */
    private Long wordbookId;
    /** 每日任务 ID */
    private Long dailyTaskId;
    /** 异步任务 ID */
    private Long asyncTaskId;
    /** 报告日期 */
    private LocalDate reportDate;
    /** 当日新词数 */
    private Integer newWordsCount;
    /** 当日复习词数 */
    private Integer reviewWordsCount;
    /** 测验正确率（百分比） */
    private BigDecimal quizAccuracy;
    /** 错词 ID 列表（JSON） */
    private String wrongWordIds;
    /** 结构化总结（JSON） */
    private String summaryJson;
    /** Markdown 格式报告内容 */
    private String markdownContent;
    /** AI 模型名称 */
    private String modelName;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
}
