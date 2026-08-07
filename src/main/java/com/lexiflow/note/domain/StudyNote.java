package com.lexiflow.note.domain;

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
 * 学习笔记实体。
 * <p>对应数据库表 {@code study_note}，存储用户的学习笔记信息。</p>
 */
@Getter
@Setter
@TableName("study_note")
public class StudyNote {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID */
    private Long userId;
    /** 来源类型（普通笔记/AI问答摘录/AI评阅摘录） */
    private StudyNoteSourceType sourceType;
    /** 来源业务结果ID */
    private Long sourceId;
    /** 词库ID */
    private Long wordbookId;
    /** 单词ID */
    private Long wordId;
    /** 笔记标题 */
    private String title;
    /** 引用快照文本 */
    private String quotedText;
    /** 笔记内容（Markdown格式） */
    private String contentMd;
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
