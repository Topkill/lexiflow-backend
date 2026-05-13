package com.lexiflow.wordbook.dto;

import com.lexiflow.wordbook.domain.WordbookType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "词库查询参数")
public record WordbookQueryRequest(
        @Schema(description = "词库类型", example = "CET4") WordbookType type,
        @Schema(description = "词库名称关键字", example = "核心") @Size(max = 64) String keyword
) {
}
