package com.lexiflow.admin.dto;

import com.lexiflow.user.domain.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Schema(description = "后台用户分页查询")
public record AdminUserQueryRequest(
        @Schema(description = "页码") @Min(1) Long page,
        @Schema(description = "每页数量") @Min(1) @Max(100) Long size,
        @Schema(description = "邮箱或昵称关键字") String keyword,
        @Schema(description = "用户状态") UserStatus status
) {
    public AdminUserQueryRequest {
        page = page == null ? 1L : page;
        size = size == null ? 20L : size;
    }
}
