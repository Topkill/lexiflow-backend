package com.lexiflow.user.dto;

import com.lexiflow.user.domain.User;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 用户资料响应 DTO。
 *
 * <p>将 {@link User} 实体转换为前端所需的资料展示格式，
 * 通过 {@link #from(User)} 静态工厂方法进行转换。</p>
 *
 * @param id        用户 ID（字符串形式）
 * @param email     邮箱
 * @param nickname  昵称
 * @param avatarUrl 头像地址
 * @param role      角色名称
 * @param status    状态名称
 */
@Schema(description = "用户资料响应")
public record UserProfileResponse(
        @Schema(description = "用户 ID", example = "1900000000000000001") String id,
        @Schema(description = "邮箱", example = "student@example.com") String email,
        @Schema(description = "昵称", example = "小李") String nickname,
        @Schema(description = "头像地址") String avatarUrl,
        @Schema(description = "角色", example = "USER") String role,
        @Schema(description = "状态", example = "ACTIVE") String status
) {
    /**
     * 将 User 实体转换为 UserProfileResponse。
     *
     * @param user 用户实体
     * @return 用户资料响应 DTO
     */
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
