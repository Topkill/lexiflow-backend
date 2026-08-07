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
 * 完形填空实体。
 * <p>对应数据库表 {@code cloze_quiz}，存储 AI 生成的完形填空题目信息。</p>
 */
@Getter
@Setter
@TableName("cloze_quiz")
public class ClozeQuiz {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID */
    private Long userId;
    /** 词库ID */
    private Long wordbookId;
    /** 每日任务ID */
    private Long dailyTaskId;
    /** 异步任务ID */
    private Long asyncTaskId;
    /** 来源类型 */
    private ClozeSourceType sourceType;
    /** 来源哈希 */
    private String sourceHash;
    /** 缓存键 */
    private String cacheKey;
    /** 缓存是否激活 */
    private Boolean cacheActive;
    /** 命中次数 */
    private Integer hitCount;
    /** 标题 */
    private String title;
    /** 文章 */
    private String passage;
    /** 候选单词（JSON格式） */
    private String candidateWords;
    /** 目标单词ID（JSON格式） */
    private String targetWordIds;
    /** 解析 */
    private String explanation;
    /** 模型名称 */
    private String modelName;
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
