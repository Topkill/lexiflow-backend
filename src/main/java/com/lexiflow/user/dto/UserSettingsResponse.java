package com.lexiflow.user.dto;

import com.lexiflow.user.domain.UserSettings;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "用户设置响应")
public record UserSettingsResponse(
        @Schema(description = "目标考试", example = "CET4") String targetExam,
        @Schema(description = "默认每日新词数", example = "30") Integer dailyNewWords,
        @Schema(description = "AI Key 使用模式", example = "PUBLIC") String aiKeyMode,
        @Schema(description = "是否启用日报入口", example = "true") Boolean enableDailyReport,
        @Schema(description = "用户时区", example = "Asia/Shanghai") String timezone
) {
    public static UserSettingsResponse from(UserSettings settings) {
        return new UserSettingsResponse(
                settings.getTargetExam() == null ? null : settings.getTargetExam().name(),
                settings.getDailyNewWords(),
                settings.getAiKeyMode().name(),
                settings.getEnableDailyReport(),
                settings.getTimezone()
        );
    }
}
