package com.lexiflow.common.controller;

import com.lexiflow.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "系统健康检查")
@RestController
@RequestMapping("/api/v1")
public class PingController {

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
