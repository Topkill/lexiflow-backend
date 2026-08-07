package com.lexiflow.admin.wordbook.dto;

import com.lexiflow.wordbook.domain.WordbookType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 后台词库保存请求 DTO。
 *
 * <p>用于创建或更新词库，包含词库的基本信息和配置。</p>
 */
@Schema(description = "后台词库保存请求")
public record AdminWordbookRequest(
        @Schema(description = "词库名称") @NotBlank @Size(max = 128) String name,
        @Schema(description = "词库编码") @NotBlank @Size(max = 64) String code,
        @Schema(description = "词库类型") @NotNull WordbookType type,
        @Schema(description = "词库描述") @Size(max = 512) String description,
        @Schema(description = "封面图") @Size(max = 512) String coverUrl,
        @Schema(description = "难度等级") @NotNull @Min(1) @Max(5) Integer difficultyLevel,
        @Schema(description = "是否启用") @NotNull Boolean enabled,
        @Schema(description = "排序") @NotNull Integer sortOrder
) {
}