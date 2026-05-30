package com.lexiflow.wordbook.importing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Schema(description = "单词导入任务查询")
public record WordImportTaskQueryRequest(
        @Schema(description = "页码") @Min(1) Long page,
        @Schema(description = "每页数量") @Min(1) @Max(100) Long size,
        @Schema(description = "词库 ID") @Min(1) Long wordbookId
) {
    public long safePage() {
        return page == null ? 1L : page;
    }

    public long safeSize() {
        return size == null ? 5L : size;
    }
}
