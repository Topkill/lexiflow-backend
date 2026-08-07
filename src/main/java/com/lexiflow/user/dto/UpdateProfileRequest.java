package com.lexiflow.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 更新用户资料请求 DTO。
 *
 * @param nickname  新昵称，必填，长度 1-64
 * @param avatarUrl 新头像地址，可选，最大 512 字符
 */
@Schema(description = "更新用户资料请求")
public record UpdateProfileRequest(
        @Schema(description = "昵称", example = "新的昵称")
        @NotBlank
        @Size(min = 1, max = 64)
        String nickname,

        @Schema(description = "头像地址")
        @Size(max = 512)
        String avatarUrl
) {
}
