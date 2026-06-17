package com.lexiflow.admin.wordbook.dto;

import com.lexiflow.wordbook.domain.Wordbook;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "后台词库响应")
public record AdminWordbookResponse(
        @Schema(description = "词库 ID") String id,
        @Schema(description = "词库名称") String name,
        @Schema(description = "词库编码") String code,
        @Schema(description = "词库类型") String type,
        @Schema(description = "描述") String description,
        @Schema(description = "封面图") String coverUrl,
        @Schema(description = "难度等级") Integer difficultyLevel,
        @Schema(description = "单词数量") Integer wordCount,
        @Schema(description = "是否启用") Boolean enabled,
        @Schema(description = "排序") Integer sortOrder,
        @Schema(description = "创建时间") LocalDateTime createdAt,
        @Schema(description = "更新时间") LocalDateTime updatedAt
) {
        /**
     * 将 Wordbook 实体对象转换为 AdminWordbookResponse 响应对象。
     *
     * @param wordbook 源单词本实体对象，不能为 null
     * @return 转换后的管理员端单词本响应对象，包含 ID、名称、编码、类型、描述、封面URL、难度等级、单词数量、启用状态、排序顺序以及创建和更新时间等信息
     */
    public static AdminWordbookResponse from(Wordbook wordbook) {
        return new AdminWordbookResponse(
                String.valueOf(wordbook.getId()),
                wordbook.getName(),
                wordbook.getCode(),
                wordbook.getType().name(),
                wordbook.getDescription(),
                wordbook.getCoverUrl(),
                wordbook.getDifficultyLevel(),
                wordbook.getWordCount(),
                wordbook.getEnabled(),
                wordbook.getSortOrder(),
                wordbook.getCreatedAt(),
                wordbook.getUpdatedAt()
        );
    }

}
