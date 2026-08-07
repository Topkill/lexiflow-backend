package com.lexiflow.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 注册请求 DTO。
 *
 * @param email    用户邮箱，必须合法且未注册
 * @param password 密码，长度 8-64，必须包含字母和数字
 * @param nickname 用户昵称，长度 1-64
 */
@Schema(description = "注册请求")
public record RegisterRequest(
        @Schema(description = "邮箱", example = "student@example.com")
        @NotBlank
        @Email
        @Size(max = 128)
        String email,

        @Schema(description = "密码", example = "Password123!")
        @NotBlank
        @Size(min = 8, max = 64)
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "必须包含字母和数字")
        String password,

        @Schema(description = "昵称", example = "小李")
        @NotBlank
        @Size(min = 1, max = 64)
        String nickname
) {
}
