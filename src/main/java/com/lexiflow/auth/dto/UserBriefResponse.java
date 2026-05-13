package com.lexiflow.auth.dto;

import com.lexiflow.user.domain.User;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "当前用户摘要")
public record UserBriefResponse(
        @Schema(description = "用户 ID", example = "1900000000000000001") String id,
        @Schema(description = "邮箱", example = "student@example.com") String email,
        @Schema(description = "昵称", example = "小李") String nickname,
        @Schema(description = "头像地址") String avatarUrl,
        @Schema(description = "角色", example = "USER") String role,
        @Schema(description = "状态", example = "ACTIVE") String status
) {
    public static UserBriefResponse from(User user) {
        return new UserBriefResponse(
                String.valueOf(user.getId()),
                user.getEmail(),
                user.getNickname(),
                user.getAvatarUrl(),
                user.getRole().name(),
                user.getStatus().name()
        );
    }
}
