package com.lexiflow.quiz.cloze.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("cloze_attempt")
public class ClozeAttempt {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long quizId;
    private Long userId;
    private Long wordbookId;
    private Integer totalBlanks;
    private Integer correctCount;
    private Integer wrongCount;
    private BigDecimal score;
    private Integer durationSeconds;
    private LocalDateTime submittedAt;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
}
