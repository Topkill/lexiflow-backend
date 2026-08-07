package com.lexiflow.note.dto;

import com.lexiflow.note.domain.StudyNoteSourceType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 学习笔记保存请求 DTO。
 * <p>用于创建或更新学习笔记。</p>
 *
 * @param sourceType 来源类型
 * @param sourceId 来源业务结果ID
 * @param wordbookId 词库ID
 * @param wordId 单词ID
 * @param title 标题
 * @param quotedText 引用快照文本
 * @param contentMd 笔记内容（Markdown格式）
 */
@Schema(description = "学习笔记保存请求")
public record StudyNoteRequest(
        @Schema(description = "来源类型：NORMAL、WORD_QA、CLOZE_REVIEW") StudyNoteSourceType sourceType,
        @Schema(description = "来源业务结果 ID") @Positive Long sourceId,
        @Schema(description = "词库 ID") @Positive Long wordbookId,
        @Schema(description = "单词 ID") @Positive Long wordId,
        @Schema(description = "标题") @Size(max = 255) String title,
        @Schema(description = "引用快照文本") String quotedText,
        @Schema(description = "我的笔记 Markdown") String contentMd
) {
}
