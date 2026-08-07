package com.lexiflow.user.dto;

import com.lexiflow.user.domain.AiKeyMode;
import com.lexiflow.user.domain.TargetExam;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 更新用户设置请求 DTO。
 *
 * <p>包含用户可修改的所有个性化配置项，所有字段均为必填。</p>
 *
 * @param targetExam      目标考试类型
 * @param dailyNewWords   每日新词数，范围 1-300
 * @param aiKeyMode       AI Key 使用模式
 * @param enableDailyReport 是否启用日报入口
 * @param timezone        用户时区，如 Asia/Shanghai
 */
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
