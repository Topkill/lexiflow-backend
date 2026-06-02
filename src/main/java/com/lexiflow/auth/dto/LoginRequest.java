package com.lexiflow.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "登录请求")
public record LoginRequest(
        @Schema(description = "邮箱", example = "student@example.com")
        @NotBlank
        @Email
        @Size(max = 128)
        String email,

        @Schema(description = "密码", example = "Password123!")
        @NotBlank
        @Size(max = 64)
        String password,

        @Schema(description = "登录验证码 ID", example = "550e8400-e29b-41d4-a716-446655440000")
        @Size(max = 64)
        String captchaId,

        @Schema(description = "登录验证码", example = "1234")
        @Size(max = 8)
        String captchaCode
) {

    public LoginRequest(String email, String password) {
        this(email, password, null, null);
    }
}
