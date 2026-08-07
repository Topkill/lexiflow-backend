package com.lexiflow.infra.properties;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * CORS 跨域配置属性。
 * <p>
 * 对应配置前缀 {@code lexiflow.cors}，用于配置跨域请求策略。
 * </p>
 *
 * @param allowedOrigins        允许的源列表
 * @param allowedOriginPatterns 允许的源模式列表
 * @param allowedMethods        允许的 HTTP 方法列表
 * @param allowedHeaders        允许的请求头列表
 * @param exposedHeaders        暴露给客户端的响应头列表
 * @param allowCredentials      是否允许携带凭证
 * @param maxAge                预检请求缓存时长（秒）
 */
@ConfigurationProperties(prefix = "lexiflow.cors")
public record CorsProperties(
        List<String> allowedOrigins,
        List<String> allowedOriginPatterns,
        List<String> allowedMethods,
        List<String> allowedHeaders,
        List<String> exposedHeaders,
        boolean allowCredentials,
        long maxAge
) {
}
