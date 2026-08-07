package com.lexiflow.wordbook.domain;

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
 * 单词实体。
 * <p>对应数据库表 {@code word}，存储词库中每个单词的详细信息，包括拼写、音标、释义、例句、短语、词根词缀等。</p>
 */
@Getter
@Setter
@TableName("word")
public class Word {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属词库ID */
    private Long wordbookId;
    /** 单词拼写 */
    private String word;
    /** 标准化拼写（小写） */
    private String normalizedWord;
    /** 音标0（英式） */
    private String phonetic0;
    /** 音标1（美式） */
    private String phonetic1;
    /** 中文释义 */
    private String trans;
    /** 例句（JSON格式） */
    private String sentences;
    /** 短语（JSON格式） */
    private String phrases;
    /** 同义词（JSON格式） */
    private String synos;
    /** 相关词 */
    private String relWords;
    /** 词根词缀 */
    private String etymology;
    /** 主要词性 */
    private String primaryPos;
    /** 主要释义 */
    private String primaryDefinition;
    /** 标签（JSON格式） */
    private String tags;
    /** 排序序号 */
    private Integer sequenceNo;
    /** 难度等级 */
    private Integer difficultyLevel;
    /** 考试出现频率 */
    private Integer examFrequency;
    /** 是否启用 */
    private Boolean enabled;
    /** 创建人ID */
    private Long createdBy;
    /** 更新人ID */
    private Long updatedBy;
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
