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
 * 完形填空空实体。
 * <p>对应数据库表 {@code cloze_quiz_blank}，存储完形填空中每个空的详细信息。</p>
 */
@Getter
@Setter
@TableName("cloze_quiz_blank")
public class ClozeQuizBlank {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 完形填空ID */
    private Long quizId;
    /** 空序号 */
    private Integer blankNo;
    /** 单词ID */
    private Long wordId;
    /** 答案单词 */
    private String answerWord;
    /** 提示 */
    private String hint;
    /** 解析 */
    private String explanation;
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
