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
 * 词库实体。
 * <p>对应数据库表 {@code wordbook}，存储词库的基本信息，包括名称、编码、类型、封面、难度等。</p>
 */
@Getter
@Setter
@TableName("wordbook")
public class Wordbook {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 词库名称 */
    private String name;
    /** 词库编码（唯一标识） */
    private String code;
    /** 词库类型（CET4/CET6/考研等） */
    private WordbookType type;
    /** 词库描述 */
    private String description;
    /** 封面图片URL */
    private String coverUrl;
    /** 难度等级 */
    private Integer difficultyLevel;
    /** 单词总数 */
    private Integer wordCount;
    /** 是否启用 */
    private Boolean enabled;
    /** 排序序号 */
    private Integer sortOrder;
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
