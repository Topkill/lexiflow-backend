package com.lexiflow.wordbook.importing.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 单词导入错误实体。
 * <p>对应数据库表 {@code word_import_error}，记录导入过程中的错误信息。</p>
 */
@Getter
@Setter
@TableName("word_import_error")
public class WordImportError {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 导入任务ID */
    private Long importTaskId;
    /** 行号 */
    private Integer rowNo;
    /** 单词文本 */
    private String wordText;
    /** 错误代码 */
    private String errorCode;
    /** 错误信息 */
    private String errorMessage;
    /** 原始数据 */
    private String rawData;
    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
