package com.lexiflow.wordbook.importing.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("word_import_error")
public class WordImportError {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long importTaskId;
    private Integer rowNo;
    private String wordText;
    private String errorCode;
    private String errorMessage;
    private String rawData;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
