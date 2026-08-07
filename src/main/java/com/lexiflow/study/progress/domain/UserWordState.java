package com.lexiflow.study.progress.domain;

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
 * 用户单词学习状态实体。
 * <p>记录用户在特定词书中对某个单词的学习进度，包括间隔重复算法所需的
 * 重复次数、间隔天数、难度因子、下次复习日期等核心参数。</p>
 */
@Getter
@Setter
@TableName("user_word_state")
public class UserWordState {

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户 ID */
    private Long userId;
    /** 词书 ID */
    private Long wordbookId;
    /** 单词 ID */
    private Long wordId;
    /** 关联的学习计划 ID */
    private Long planId;
    /** 掌握状态 */
    private MasteryStatus masteryStatus;
    /** 是否已学习过 */
    private Boolean learned;
    /** 连续正确回答次数 */
    private Integer repetition;
    /** 当前复习间隔天数 */
    private Integer intervalDays;
    /** 难度因子（SM-2 算法核心参数） */
    private BigDecimal easinessFactor;
    /** 下次复习日期 */
    private LocalDate nextReviewDate;
    /** 最后一次学习反馈 */
    private StudyFeedback lastFeedback;
    /** 最后一次学习时间 */
    private LocalDateTime lastStudiedAt;
    /** 最后一次复习时间 */
    private LocalDateTime lastReviewedAt;
    /** 累计错误次数 */
    private Integer wrongCount;
    /** 累计正确次数 */
    private Integer correctCount;
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
