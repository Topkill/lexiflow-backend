package com.lexiflow.auth.controller;

import com.lexiflow.auth.dto.LoginRequest;
import com.lexiflow.auth.dto.LoginCaptchaResponse;
import com.lexiflow.auth.dto.LoginResponse;
import com.lexiflow.auth.dto.RegisterRequest;
import com.lexiflow.auth.dto.RegisterResponse;
import com.lexiflow.auth.dto.UserBriefResponse;
import com.lexiflow.auth.security.AuthContext;
import com.lexiflow.auth.service.AuthService;
import com.lexiflow.auth.service.LoginCaptchaService;
import com.lexiflow.common.api.ApiResponse;
import com.lexiflow.common.error.ErrorCode;
import com.lexiflow.common.exception.BizException;
import com.lexiflow.common.util.ServletUtils;
import com.lexiflow.infra.properties.AuthCookieProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口控制器。
 *
 * <p>提供注册、登录、验证码获取、Token 刷新、退出登录及当前用户查询等接口。
 * 登录成功后通过 HttpOnly Cookie 下发 Refresh Token，Access Token 通过响应体返回。</p>
 */
@Tag(name = "认证接口")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController {

    /** Refresh Token 的 Cookie 名称 */
    private static final String REFRESH_TOKEN_COOKIE = "refresh_token";
    /** Authorization 头中 Bearer Token 的前缀 */
    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthService authService;
    private final LoginCaptchaService loginCaptchaService;
    private final AuthCookieProperties authCookieProperties;

    /** 邮箱注册，创建新用户并返回用户摘要 */
    @Operation(summary = "邮箱注册")
    @PostMapping("/register")
    public ApiResponse<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.success(authService.register(request));
    }

    /** 密码登录，验证成功后下发 Access Token 和 Refresh Token Cookie */
    @Operation(summary = "密码登录")
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse
    ) {
        AuthService.LoginResult result = authService.login(request, ServletUtils.clientIp(servletRequest));
        servletResponse.addHeader(HttpHeaders.SET_COOKIE, buildRefreshCookie(result.refreshToken(), result.refreshTokenTtlSeconds()).toString());
        return ApiResponse.success(result.response());
    }

    /** 获取登录图形验证码（Base64 Data URL 格式） */
    @Operation(summary = "获取登录图形验证码")
    @GetMapping("/login-captcha")
    public ApiResponse<LoginCaptchaResponse> loginCaptcha(HttpServletRequest servletRequest) {
        return ApiResponse.success(loginCaptchaService.issue(ServletUtils.clientIp(servletRequest)));
    }

    /** 使用 Refresh Token 刷新 Access Token */
    @Operation(summary = "刷新访问令牌")
    @PostMapping("/refresh")
    public ApiResponse<LoginResponse> refresh(@CookieValue(name = REFRESH_TOKEN_COOKIE, required = false) String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return ApiResponse.success(authService.refresh(refreshToken));
    }

    /** 退出登录，撤销 Access Token 和 Refresh Token，并清除 Refresh Token Cookie */
    @Operation(summary = "退出登录")
    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            @CookieValue(name = REFRESH_TOKEN_COOKIE, required = false) String refreshToken,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        authService.logout(refreshToken, resolveBearerToken(request));
        response.addHeader(HttpHeaders.SET_COOKIE, buildRefreshCookie("", 0).toString());
        return ApiResponse.success();
    }

    /** 查询当前登录用户的摘要信息 */
    @Operation(summary = "当前用户")
    @GetMapping("/me")
    public ApiResponse<UserBriefResponse> me() {
        return ApiResponse.success(authService.currentUser(AuthContext.currentUserId()));
    }

    /** 构建 Refresh Token Cookie，支持配置 httpOnly、secure、sameSite 等属性 */
    private ResponseCookie buildRefreshCookie(String token, long maxAgeSeconds) {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE, token)
                .httpOnly(true)
                .secure(authCookieProperties.secure())
                .sameSite(authCookieProperties.sameSite())
                .path(authCookieProperties.path())
                .maxAge(maxAgeSeconds)
                .build();
    }

    /** 从 Authorization 请求头中提取 Bearer Token */
    private String resolveBearerToken(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return null;
        }
        return authorization.substring(BEARER_PREFIX.length());
    }
}
