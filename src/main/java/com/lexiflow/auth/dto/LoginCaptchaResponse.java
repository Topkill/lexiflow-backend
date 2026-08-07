package com.lexiflow.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 登录图形验证码响应 DTO。
 *
 * @param captchaId      验证码 ID，用于后续校验
 * @param imageDataUrl   验证码图片的 Base64 Data URL
 * @param expiresInSeconds 验证码有效期（秒）
 */
@Schema(description = "登录图形验证码")
public record LoginCaptchaResponse(
        @Schema(description = "验证码 ID")
        String captchaId,

        @Schema(description = "验证码图片 Data URL")
        String imageDataUrl,

        @Schema(description = "有效期秒数", example = "300")
        long expiresInSeconds
) {
}
