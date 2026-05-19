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

@Getter
@Setter
@TableName("user_word_state")
public class UserWordState {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private Long wordbookId;
    private Long wordId;
    private Long planId;
    private MasteryStatus masteryStatus;
    private Boolean learned;
    private Integer repetition;
    private Integer intervalDays;
    private BigDecimal easinessFactor;
    private LocalDate nextReviewDate;
    private StudyFeedback lastFeedback;
    private LocalDateTime lastStudiedAt;
    private LocalDateTime lastReviewedAt;
    private Integer wrongCount;
    private Integer correctCount;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
}
