package com.lexiflow.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 修改密码请求 DTO。
 *
 * <p>包含旧密码和新密码，新密码必须包含字母和数字，长度 8-64 位。</p>
 *
 * @param oldPassword 旧密码，用于验证用户身份
 * @param newPassword 新密码，需满足复杂度要求（字母 + 数字）
 */
@Schema(description = "修改密码请求")
public record ChangePasswordRequest(
        @Schema(description = "旧密码", example = "OldPassword123!")
        @NotBlank
        @Size(max = 64)
        String oldPassword,

        @Schema(description = "新密码", example = "NewPassword123!")
        @NotBlank
        @Size(min = 8, max = 64)
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "必须包含字母和数字")
        String newPassword
) {
}
