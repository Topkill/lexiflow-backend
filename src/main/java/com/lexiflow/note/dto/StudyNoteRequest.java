package com.lexiflow.note.dto;

import com.lexiflow.note.domain.StudyNoteSourceType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

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
