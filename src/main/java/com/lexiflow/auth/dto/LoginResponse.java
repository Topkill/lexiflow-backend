package com.lexiflow.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "登录响应")
public record LoginResponse(
        @Schema(description = "访问令牌") String accessToken,
        @Schema(description = "过期秒数", example = "7200") long expiresIn,
        @Schema(description = "CSRF Token，MVP 阶段预留") String csrfToken,
        @Schema(description = "用户信息") UserBriefResponse user
) {
}
