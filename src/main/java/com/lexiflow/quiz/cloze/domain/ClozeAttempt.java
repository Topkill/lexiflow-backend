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

/**
 * 完形填空作答实体。
 * <p>对应数据库表 {@code cloze_attempt}，记录用户的完形填空作答信息。</p>
 */
@Getter
@Setter
@TableName("cloze_attempt")
public class ClozeAttempt {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 完形填空ID */
    private Long quizId;
    /** 用户ID */
    private Long userId;
    /** 词库ID */
    private Long wordbookId;
    /** 总空数 */
    private Integer totalBlanks;
    /** 正确数 */
    private Integer correctCount;
    /** 错误数 */
    private Integer wrongCount;
    /** 得分 */
    private BigDecimal score;
    /** 作答时长（秒） */
    private Integer durationSeconds;
    /** 提交时间 */
    private LocalDateTime submittedAt;
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
