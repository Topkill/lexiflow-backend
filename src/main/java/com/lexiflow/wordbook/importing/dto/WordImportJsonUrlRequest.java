package com.lexiflow.wordbook.importing.dto;

import com.lexiflow.wordbook.importing.domain.WordImportDuplicateStrategy;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "JSON URL 词库导入请求")
public record WordImportJsonUrlRequest(
        @Schema(description = "远程 JSON 文件 URL") @NotBlank @Size(max = 512) String sourceUrl,
        @Schema(description = "重复处理策略") WordImportDuplicateStrategy duplicateStrategy,
        @Schema(description = "是否先替换目标词库内现有关联") Boolean replaceWordbook
) {
    public WordImportJsonUrlRequest {
        duplicateStrategy = duplicateStrategy == null ? WordImportDuplicateStrategy.SKIP : duplicateStrategy;
        replaceWordbook = Boolean.TRUE.equals(replaceWordbook);
    }
}
