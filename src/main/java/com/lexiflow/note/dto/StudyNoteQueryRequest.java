package com.lexiflow.note.dto;

import com.lexiflow.note.domain.StudyNoteSourceType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

/**
 * 学习笔记分页查询请求 DTO。
 * <p>用于筛选和分页查询学习笔记列表。</p>
 *
 * @param sourceType 来源类型
 * @param wordbookId 词库ID
 * @param wordId 单词ID
 * @param keyword 关键词
 * @param page 当前页码，默认为1
 * @param size 每页数量，默认为20，最大100
 */
@Schema(description = "学习笔记分页查询参数")
public record StudyNoteQueryRequest(
        @Schema(description = "来源类型：NORMAL、WORD_QA、CLOZE_REVIEW") StudyNoteSourceType sourceType,
        @Schema(description = "词库 ID") @Positive Long wordbookId,
        @Schema(description = "单词 ID") @Positive Long wordId,
        @Schema(description = "关键词") String keyword,
        @Schema(description = "当前页码", example = "1") @Min(1) Long page,
        @Schema(description = "每页数量", example = "20") @Min(1) @Max(100) Long size
) {
    public StudyNoteQueryRequest {
        page = page == null ? 1L : page;
        size = size == null ? 20L : size;
    }
}
