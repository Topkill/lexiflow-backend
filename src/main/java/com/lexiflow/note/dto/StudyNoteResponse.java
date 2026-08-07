package com.lexiflow.note.dto;

import com.lexiflow.note.domain.StudyNote;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/**
 * 学习笔记响应 DTO。
 * <p>返回学习笔记的详细信息。</p>
 *
 * @param noteId 笔记ID
 * @param sourceType 来源类型
 * @param sourceId 来源业务结果ID
 * @param wordbookId 词库ID
 * @param wordId 单词ID
 * @param title 标题
 * @param quotedText 引用快照文本
 * @param contentMd 笔记内容（Markdown格式）
 * @param createdAt 创建时间
 * @param updatedAt 更新时间
 */
@Schema(description = "学习笔记响应")
public record StudyNoteResponse(
        @Schema(description = "笔记 ID") String noteId,
        @Schema(description = "来源类型") String sourceType,
        @Schema(description = "来源业务结果 ID") String sourceId,
        @Schema(description = "词库 ID") String wordbookId,
        @Schema(description = "单词 ID") String wordId,
        @Schema(description = "标题") String title,
        @Schema(description = "引用快照文本") String quotedText,
        @Schema(description = "我的笔记 Markdown") String contentMd,
        @Schema(description = "创建时间") LocalDateTime createdAt,
        @Schema(description = "更新时间") LocalDateTime updatedAt
) {
    public static StudyNoteResponse from(StudyNote note) {
        return new StudyNoteResponse(
                String.valueOf(note.getId()),
                note.getSourceType() == null ? null : note.getSourceType().name(),
                note.getSourceId() == null ? null : String.valueOf(note.getSourceId()),
                note.getWordbookId() == null ? null : String.valueOf(note.getWordbookId()),
                note.getWordId() == null ? null : String.valueOf(note.getWordId()),
                note.getTitle(),
                note.getQuotedText(),
                note.getContentMd(),
                note.getCreatedAt(),
                note.getUpdatedAt()
        );
    }
}
