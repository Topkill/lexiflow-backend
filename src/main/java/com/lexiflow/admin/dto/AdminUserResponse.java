package com.lexiflow.admin.dto;

import com.lexiflow.user.domain.User;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "后台用户响应")
public record AdminUserResponse(
        @Schema(description = "用户 ID") String id,
        @Schema(description = "邮箱") String email,
        @Schema(description = "昵称") String nickname,
        @Schema(description = "头像") String avatarUrl,
        @Schema(description = "角色") String role,
        @Schema(description = "状态") String status,
        @Schema(description = "最近登录时间") LocalDateTime lastLoginAt,
        @Schema(description = "创建时间") LocalDateTime createdAt
) {
    /**
     * 将领域用户对象转换为管理员用户响应对象。
     *
     * @param user 领域用户对象，包含用户的基本信息、角色、状态及时间戳等数据
     * @return 转换后的管理员用户响应对象，其中ID、角色和状态被转换为字符串格式，其他字段直接映射
     */
    public static AdminUserResponse from(User user) {
        return new AdminUserResponse(
                String.valueOf(user.getId()),
                user.getEmail(),
                user.getNickname(),
                user.getAvatarUrl(),
                user.getRole().name(),
                user.getStatus().name(),
                user.getLastLoginAt(),
                user.getCreatedAt()
        );
    }
}
