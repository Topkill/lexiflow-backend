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
@TableName("word_import_task")
public class WordImportTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long asyncTaskId;
    private Long wordbookId;
    private String fileName;
    private String filePath;
    private WordImportSourceType sourceType;
    private String requestJson;
    private WordImportDuplicateStrategy duplicateStrategy;
    private WordImportStatus status;
    private Integer totalRows;
    private Integer successRows;
    private Integer failedRows;
    private String errorReportPath;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Long createdBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
