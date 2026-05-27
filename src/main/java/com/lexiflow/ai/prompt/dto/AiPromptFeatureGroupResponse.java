package com.lexiflow.ai.prompt.dto;

import com.lexiflow.ai.prompt.domain.AiPromptFeatureType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "AI 提示词功能分组响应")
public record AiPromptFeatureGroupResponse(
        @Schema(description = "功能类型") AiPromptFeatureType featureType,
        @Schema(description = "功能名称") String featureLabel,
        @Schema(description = "生效词书 ID，0 表示全部词书") String wordbookId,
        @Schema(description = "当前是否使用默认模板") Boolean usingDefault,
        @Schema(description = "当前生效模板 ID") String activeTemplateId,
        @Schema(description = "默认提示词") AiPromptTemplateResponse builtinTemplate,
        @Schema(description = "从全部词书继承的当前生效模板，仅具体词书范围可能返回") AiPromptTemplateResponse inheritedTemplate,
        @Schema(description = "自定义模板列表") List<AiPromptTemplateResponse> templates
) {
}
