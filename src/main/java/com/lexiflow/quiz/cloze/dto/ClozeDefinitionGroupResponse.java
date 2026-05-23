package com.lexiflow.quiz.cloze.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "完形填空单词释义分组")
public record ClozeDefinitionGroupResponse(
        @Schema(description = "词性") String pos,
        @Schema(description = "中文释义") List<String> definitions
) {
}
