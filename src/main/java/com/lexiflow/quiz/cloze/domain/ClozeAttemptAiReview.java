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
 * 完形填空 AI 评阅实体。
 * <p>对应数据库表 {@code cloze_attempt_ai_review}，存储 AI 对完形填空作答的评阅结果。</p>
 */
@Getter
@Setter
@TableName("cloze_attempt_ai_review")
public class ClozeAttemptAiReview {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 作答ID */
    private Long attemptId;
    /** 完形填空ID */
    private Long quizId;
    /** 用户ID */
    private Long userId;
    /** 词库ID */
    private Long wordbookId;
    /** 来源哈希 */
    private String sourceHash;
    /** 评阅内容（JSON格式） */
    private String contentJson;
    /** 评阅状态 */
    private ClozeAttemptAiReviewStatus status;
    /** 模型名称 */
    private String modelName;
    /** 错误信息 */
    private String errorMessage;
    /** 开始时间 */
    private LocalDateTime startedAt;
    /** 完成时间 */
    private LocalDateTime finishedAt;
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
