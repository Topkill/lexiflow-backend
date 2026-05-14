package com.lexiflow.wordbook.importing.dto;

import com.lexiflow.wordbook.importing.domain.WordImportTask;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "单词导入任务响应")
public record WordImportTaskResponse(
        @Schema(description = "导入任务 ID") String id,
        @Schema(description = "词库 ID") String wordbookId,
        @Schema(description = "文件名") String fileName,
        @Schema(description = "重复处理策略") String duplicateStrategy,
        @Schema(description = "状态") String status,
        @Schema(description = "总行数") Integer totalRows,
        @Schema(description = "成功行数") Integer successRows,
        @Schema(description = "失败行数") Integer failedRows,
        @Schema(description = "开始时间") LocalDateTime startedAt,
        @Schema(description = "结束时间") LocalDateTime finishedAt,
        @Schema(description = "创建时间") LocalDateTime createdAt
) {
    public static WordImportTaskResponse from(WordImportTask task) {
        return new WordImportTaskResponse(
                String.valueOf(task.getId()),
                String.valueOf(task.getWordbookId()),
                task.getFileName(),
                task.getDuplicateStrategy().name(),
                task.getStatus().name(),
                task.getTotalRows(),
                task.getSuccessRows(),
                task.getFailedRows(),
                task.getStartedAt(),
                task.getFinishedAt(),
                task.getCreatedAt()
        );
    }
}