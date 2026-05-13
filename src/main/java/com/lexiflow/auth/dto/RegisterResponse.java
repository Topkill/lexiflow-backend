package com.lexiflow.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "注册响应")
public record RegisterResponse(
        @Schema(description = "用户 ID", example = "1900000000000000001") String userId,
        @Schema(description = "邮箱", example = "student@example.com") String email,
        @Schema(description = "昵称", example = "小李") String nickname
) {
}
