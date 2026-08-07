package com.lexiflow.common.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

/**
 * Servlet 工具类。
 * <p>
 * 提供与 HTTP 请求相关的常用工具方法。
 * </p>
 */
public final class ServletUtils {

    private ServletUtils() {
    }

    /**
     * 获取客户端真实 IP 地址。
     * <p>
     * 优先从 {@code X-Forwarded-For} 头获取（支持代理链），其次从 {@code X-Real-IP} 头获取，
     * 最后回退到 {@code request.getRemoteAddr()}。
     * </p>
     *
     * @param request HTTP 请求对象
     * @return 客户端 IP 地址
     */
    public static String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            return forwardedFor.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(realIp)) {
            return realIp;
        }
        return request.getRemoteAddr();
    }
}
