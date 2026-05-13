package com.lexiflow.user.dto;

import com.lexiflow.user.domain.AiKeyMode;
import com.lexiflow.user.domain.TargetExam;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "更新用户设置请求")
public record UpdateUserSettingsRequest(
        @Schema(description = "目标考试", example = "CET4") TargetExam targetExam,

        @Schema(description = "默认每日新词数", example = "30")
        @NotNull
        @Min(1)
        @Max(300)
        Integer dailyNewWords,

        @Schema(description = "AI Key 使用模式", example = "PUBLIC")
        @NotNull
        AiKeyMode aiKeyMode,

        @Schema(description = "是否启用日报入口", example = "true")
        @NotNull
        Boolean enableDailyReport,

        @Schema(description = "用户时区", example = "Asia/Shanghai")
        @NotNull
        @Size(min = 1, max = 64)
        String timezone
) {
}
