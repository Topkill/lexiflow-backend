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

/**
 * 完形填空作答答案实体。
 * <p>对应数据库表 {@code cloze_attempt_answer}，记录用户每个空的作答详情。</p>
 */
@Getter
@Setter
@TableName("cloze_attempt_answer")
public class ClozeAttemptAnswer {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 作答ID */
    private Long attemptId;
    /** 完形填空ID */
    private Long quizId;
    /** 空ID */
    private Long blankId;
    /** 单词ID */
    private Long wordId;
    /** 用户答案 */
    private String userAnswer;
    /** 正确答案 */
    private String correctAnswer;
    /** 是否正确 */
    private Boolean correct;
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
