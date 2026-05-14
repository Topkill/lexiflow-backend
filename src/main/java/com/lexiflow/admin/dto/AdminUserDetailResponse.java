package com.lexiflow.admin.dto;

import com.lexiflow.user.domain.User;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "后台用户详情响应")
public record AdminUserDetailResponse(
        @Schema(description = "用户 ID") String id,
        @Schema(description = "邮箱") String email,
        @Schema(description = "昵称") String nickname,
        @Schema(description = "头像") String avatarUrl,
        @Schema(description = "角色") String role,
        @Schema(description = "状态") String status,
        @Schema(description = "最近登录时间") LocalDateTime lastLoginAt,
        @Schema(description = "最近登录 IP") String lastLoginIp,
        @Schema(description = "创建时间") LocalDateTime createdAt,
        @Schema(description = "累计学习事件数") Long studyEventCount,
        @Schema(description = "AI 调用次数") Long aiCallCount
) {
    public static AdminUserDetailResponse of(User user, long studyEventCount, long aiCallCount) {
        return new AdminUserDetailResponse(
                String.valueOf(user.getId()),
                user.getEmail(),
                user.getNickname(),
                user.getAvatarUrl(),
                user.getRole().name(),
                user.getStatus().name(),
                user.getLastLoginAt(),
                user.getLastLoginIp(),
                user.getCreatedAt(),
                studyEventCount,
                aiCallCount
        );
    }
}