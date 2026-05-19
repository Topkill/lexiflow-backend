package com.lexiflow.quiz.cloze.domain;

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
@TableName("cloze_attempt_answer")
public class ClozeAttemptAnswer {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long attemptId;
    private Long quizId;
    private Long blankId;
    private Long wordId;
    private String userAnswer;
    private String correctAnswer;
    private Boolean correct;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
}
