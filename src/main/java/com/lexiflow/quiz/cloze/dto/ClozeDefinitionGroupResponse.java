package com.lexiflow.quiz.cloze.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 完形填空单词释义分组响应 DTO。
 * <p>按词性分组展示单词的中文释义。</p>
 *
 * @param pos 词性
 * @param definitions 中文释义列表
 */
@Schema(description = "完形填空单词释义分组")
public record ClozeDefinitionGroupResponse(
        @Schema(description = "词性") String pos,
        @Schema(description = "中文释义") List<String> definitions
) {
}
