package com.lexiflow.study.progress.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 学习事件实体。
 * <p>记录每次学习行为的详细事件，包括场景、反馈、正确性、用时等。</p>
 */
@Getter
@Setter
@TableName("study_event")
public class StudyEvent {

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户 ID */
    private Long userId;
    /** 学习计划 ID */
    private Long planId;
    /** 词书 ID */
    private Long wordbookId;
    /** 单词 ID */
    private Long wordId;
    /** 每日任务 ID */
    private Long dailyTaskId;
    /** 每日任务项 ID */
    private Long dailyTaskItemId;
    /** 学习场景 */
    private StudyScene scene;
    /** 学习反馈（认识/不认识） */
    private StudyFeedback feedback;
    /** 质量分数 */
    private Integer qualityScore;
    /** 是否正确 */
    private Boolean isCorrect;
    /** 学习用时（秒） */
    private Integer durationSeconds;
    /** 来源引用 ID（如作答 ID） */
    private Long sourceRefId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
