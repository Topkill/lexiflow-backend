package com.lexiflow.user.dto;

import com.lexiflow.user.domain.UserSettings;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 用户设置响应 DTO。
 *
 * <p>将 {@link UserSettings} 实体转换为前端所需的设置展示格式，
 * 枚举类型以字符串形式返回。通过 {@link #from(UserSettings)} 静态工厂方法进行转换。</p>
 *
 * @param targetExam      目标考试名称
 * @param dailyNewWords   每日新词数
 * @param aiKeyMode       AI Key 使用模式名称
 * @param enableDailyReport 是否启用日报入口
 * @param timezone        用户时区
 */
@Schema(description = "用户设置响应")
public record UserSettingsResponse(
        @Schema(description = "目标考试", example = "CET4") String targetExam,
        @Schema(description = "默认每日新词数", example = "30") Integer dailyNewWords,
        @Schema(description = "AI Key 使用模式", example = "PUBLIC") String aiKeyMode,
        @Schema(description = "是否启用日报入口", example = "true") Boolean enableDailyReport,
        @Schema(description = "用户时区", example = "Asia/Shanghai") String timezone
) {
    /**
     * 将 UserSettings 实体转换为 UserSettingsResponse。
     *
     * @param settings 用户设置实体
     * @return 用户设置响应 DTO
     */
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
