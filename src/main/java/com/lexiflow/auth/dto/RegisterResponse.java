package com.lexiflow.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 注册响应 DTO。
 *
 * @param userId   新创建的用户 ID
 * @param email    用户邮箱
 * @param nickname 用户昵称
 */
@Schema(description = "注册响应")
public record RegisterResponse(
        @Schema(description = "用户 ID", example = "1900000000000000001") String userId,
        @Schema(description = "邮箱", example = "student@example.com") String email,
        @Schema(description = "昵称", example = "小李") String nickname
) {
}
