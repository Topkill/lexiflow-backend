package com.lexiflow.common.controller;

import com.lexiflow.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统健康检查控制器。
 * <p>
 * 提供 {@code /api/v1/ping} 接口，用于服务连通性检测。
 * </p>
 */
@Tag(name = "系统健康检查")
@RestController
@RequestMapping("/api/v1")
public class PingController {

    /**
     * 服务连通性检查接口。
     * <p>
     * 返回应用名称、状态和当前服务器时间，用于确认服务正常运行。
     * </p>
     *
     * @return 包含服务状态信息的响应
     */
    @Operation(summary = "服务连通性检查")
    @GetMapping("/ping")
    public ApiResponse<Map<String, Object>> ping() {
        return ApiResponse.success(Map.of(
                "app", "lexiflow-backend",
                "status", "ok",
                "time", LocalDateTime.now()
        ));
    }
}
