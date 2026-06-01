package com.lexiflow.note.dto;

import com.lexiflow.note.domain.StudyNoteSourceType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

@Schema(description = "学习笔记分页查询参数")
public record StudyNoteQueryRequest(
        @Schema(description = "来源类型：NORMAL、WORD_QA、CLOZE_REVIEW") StudyNoteSourceType sourceType,
        @Schema(description = "词库 ID") @Positive Long wordbookId,
        @Schema(description = "单词 ID") @Positive Long wordId,
        @Schema(description = "关键词") String keyword,
        @Schema(description = "当前页码", example = "1") @Min(1) Long page,
        @Schema(description = "每页数量", example = "20") @Min(1) @Max(100) Long size
) {
    public long safePage() {
        return page == null ? 1 : page;
    }

    public long safeSize() {
        return size == null ? 20 : size;
    }
}
