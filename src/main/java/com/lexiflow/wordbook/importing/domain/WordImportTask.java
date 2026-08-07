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
 * 单词导入任务实体。
 * <p>对应数据库表 {@code word_import_task}，记录单词导入任务的执行信息和统计结果。</p>
 */
@Getter
@Setter
@TableName("word_import_task")
public class WordImportTask {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 异步任务ID */
    private Long asyncTaskId;
    /** 目标词库ID */
    private Long wordbookId;
    /** 文件名 */
    private String fileName;
    /** 文件路径 */
    private String filePath;
    /** 来源类型（Excel/JSON URL） */
    private WordImportSourceType sourceType;
    /** 请求参数（JSON格式） */
    private String requestJson;
    /** 重复处理策略 */
    private WordImportDuplicateStrategy duplicateStrategy;
    /** 任务状态 */
    private WordImportStatus status;
    /** 总行数 */
    private Integer totalRows;
    /** 成功行数 */
    private Integer successRows;
    /** 失败行数 */
    private Integer failedRows;
    /** 错误报告路径 */
    private String errorReportPath;
    /** 开始时间 */
    private LocalDateTime startedAt;
    /** 完成时间 */
    private LocalDateTime finishedAt;
    /** 创建人ID */
    private Long createdBy;
    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
