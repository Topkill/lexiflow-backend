package com.lexiflow.infra.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexiflow.auth.security.JwtAuthenticationFilter;
import com.lexiflow.common.api.ApiResponse;
import com.lexiflow.common.error.ErrorCode;
import com.lexiflow.infra.properties.CorsProperties;
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

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final ObjectMapper objectMapper;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CorsProperties corsProperties;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/v1/ping",
                                "/api/v1/auth/register",
                                "/api/v1/auth/login",
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

    @Bean
    public UserDetailsService userDetailsService() {
        return username -> {
            throw new UsernameNotFoundException("不支持默认用户名密码登录");
        };
    }

    private void writeErrorResponse(HttpServletResponse response, int status, ErrorCode errorCode) throws java.io.IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ApiResponse.fail(errorCode));
    }

    private List<String> emptyToNull(List<String> values) {
        List<String> sanitized = sanitize(values);
        return CollectionUtils.isEmpty(sanitized) ? null : sanitized;
    }

    private List<String> defaultIfEmpty(List<String> values, List<String> defaults) {
        List<String> sanitized = sanitize(values);
        return CollectionUtils.isEmpty(sanitized) ? defaults : sanitized;
    }

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
