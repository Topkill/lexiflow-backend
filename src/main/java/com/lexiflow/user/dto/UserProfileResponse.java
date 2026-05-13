package com.lexiflow.user.dto;

import com.lexiflow.user.domain.User;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "用户资料响应")
public record UserProfileResponse(
        @Schema(description = "用户 ID", example = "1900000000000000001") String id,
        @Schema(description = "邮箱", example = "student@example.com") String email,
        @Schema(description = "昵称", example = "小李") String nickname,
        @Schema(description = "头像地址") String avatarUrl,
        @Schema(description = "角色", example = "USER") String role,
        @Schema(description = "状态", example = "ACTIVE") String status
) {
    public static UserProfileResponse from(User user) {
        return new UserProfileResponse(
                String.valueOf(user.getId()),
                user.getEmail(),
                user.getNickname(),
                user.getAvatarUrl(),
                user.getRole().name(),
                user.getStatus().name()
        );
    }
}
