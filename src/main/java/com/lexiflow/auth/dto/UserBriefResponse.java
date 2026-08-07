package com.lexiflow.auth.dto;

import com.lexiflow.user.domain.User;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 当前用户摘要响应 DTO。
 *
 * <p>用于登录响应和当前用户查询接口，展示用户的基本信息。
 * 通过 {@link #from(User)} 静态工厂方法从 User 实体创建。</p>
 *
 * @param id        用户 ID
 * @param email     邮箱
 * @param nickname  昵称
 * @param avatarUrl 头像地址
 * @param role      角色名称
 * @param status    状态名称
 */
@Schema(description = "当前用户摘要")
public record UserBriefResponse(
        @Schema(description = "用户 ID", example = "1900000000000000001") String id,
        @Schema(description = "邮箱", example = "student@example.com") String email,
        @Schema(description = "昵称", example = "小李") String nickname,
        @Schema(description = "头像地址") String avatarUrl,
        @Schema(description = "角色", example = "USER") String role,
        @Schema(description = "状态", example = "ACTIVE") String status
) {
    /** 从 User 实体创建 UserBriefResponse */
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
