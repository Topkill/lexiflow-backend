package com.lexiflow.infra.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexiflow.auth.security.JwtAuthenticationFilter;
import com.lexiflow.common.api.ApiResponse;
import com.lexiflow.common.error.ErrorCode;
import com.lexiflow.infra.properties.CorsProperties;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Spring Security 安全配置类。
 * <p>
 * 配置 HTTP 安全策略，包括：
 * <ul>
 *   <li>禁用 CSRF、表单登录、HTTP Basic 和默认登出</li>
 *   <li>无状态会话管理</li>
 *   <li>URL 访问权限控制（公开接口、管理员接口、认证接口）</li>
 *   <li>CORS 跨域配置</li>
 *   <li>JWT 认证过滤器集成</li>
 *   <li>统一异常响应处理</li>
 * </ul>
 * </p>
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final ObjectMapper objectMapper;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CorsProperties corsProperties;

    /**
     * 配置安全过滤链。
     * <p>
     * 定义请求授权规则、异常处理策略和 JWT 过滤器位置。
     * </p>
     *
     * @param http HTTP 安全配置对象
     * @return 安全过滤链
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.ASYNC).permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers(
                                "/api/v1/ping",
                                "/api/v1/auth/register",
                                "/api/v1/auth/login",
                                "/api/v1/auth/login-captcha",
                                "/api/v1/auth/refresh",
                                "/api/v1/wordbooks/**",
                                "/actuator/health",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/doc.html",
                                "/webjars/**"
                        ).permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) -> writeErrorResponse(
                                response,
                                HttpServletResponse.SC_UNAUTHORIZED,
                                ErrorCode.UNAUTHORIZED
                        ))
                        .accessDeniedHandler((request, response, accessDeniedException) -> writeErrorResponse(
                                response,
                                HttpServletResponse.SC_FORBIDDEN,
                                ErrorCode.FORBIDDEN
                        ))
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * 配置 CORS 跨域策略。
     * <p>
     * 从 {@link CorsProperties} 读取允许的源、方法、头部等配置。
     * </p>
     *
     * @return CORS 配置源
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(emptyToNull(corsProperties.allowedOrigins()));
        configuration.setAllowedOriginPatterns(emptyToNull(corsProperties.allowedOriginPatterns()));
        configuration.setAllowedMethods(defaultIfEmpty(corsProperties.allowedMethods(), List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")));
        configuration.setAllowedHeaders(defaultIfEmpty(corsProperties.allowedHeaders(), List.of("Authorization", "Content-Type", "X-CSRF-Token")));
        configuration.setExposedHeaders(defaultIfEmpty(corsProperties.exposedHeaders(), List.of("Content-Disposition")));
        configuration.setAllowCredentials(corsProperties.allowCredentials());
        configuration.setMaxAge(corsProperties.maxAge());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * 配置用户详情服务。
     * <p>
     * 禁用默认用户名密码登录，仅支持自定义认证流程。
     * </p>
     *
     * @return 用户详情服务
     */
    @Bean
    public UserDetailsService userDetailsService() {
        return username -> {
            throw new UsernameNotFoundException("不支持默认用户名密码登录");
        };
    }

    /** 向响应写入统一格式的错误响应 JSON。 */
    private void writeErrorResponse(HttpServletResponse response, int status, ErrorCode errorCode) throws java.io.IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ApiResponse.fail(errorCode));
    }

    /** 将空列表转换为 null，用于 CORS 配置。 */
    private List<String> emptyToNull(List<String> values) {
        List<String> sanitized = sanitize(values);
        return CollectionUtils.isEmpty(sanitized) ? null : sanitized;
    }

    /** 如果列表为空则使用默认值。 */
    private List<String> defaultIfEmpty(List<String> values, List<String> defaults) {
        List<String> sanitized = sanitize(values);
        return CollectionUtils.isEmpty(sanitized) ? defaults : sanitized;
    }

    /** 清理列表中的空值和空白字符串。 */
    private List<String> sanitize(List<String> values) {
        if (CollectionUtils.isEmpty(values)) {
            return List.of();
        }
        return values.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }
}
