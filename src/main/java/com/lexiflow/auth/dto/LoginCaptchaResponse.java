package com.lexiflow.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

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
