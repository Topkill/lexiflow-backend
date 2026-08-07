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

/**
 * 学习计划实体。
 * <p>记录用户的学习计划配置、进度统计和状态信息，支持乐观锁并发控制。</p>
 */
@Getter
@Setter
@TableName("study_plan")
public class StudyPlan {

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户 ID */
    private Long userId;
    /** 词书 ID */
    private Long wordbookId;
    /** 计划名称 */
    private String name;
    /** 每组新词数 */
    private Integer newWordsPerGroup;
    /** 每组复习词数 */
    private Integer reviewWordsPerGroup;
    /** 计划开始日期 */
    private LocalDate startDate;
    /** 预计完成日期 */
    private LocalDate expectedFinishDate;
    /** 实际完成日期 */
    private LocalDate actualFinishDate;
    /** 计划状态 */
    private StudyPlanStatus status;
    /** 词书总词数 */
    private Integer totalWords;
    /** 已学新词数 */
    private Integer learnedCount;
    /** 已复习词数 */
    private Integer reviewedCount;
    /** 已掌握词数 */
    private Integer masteredCount;
    /** 当前学习位置（已生成的最大序号） */
    private Integer currentSequenceNo;
    /** 是否为主计划 */
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
