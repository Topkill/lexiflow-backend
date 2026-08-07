package com.lexiflow.auth.service;

import com.lexiflow.auth.dto.LoginRequest;
import com.lexiflow.auth.dto.LoginResponse;
import com.lexiflow.auth.dto.RegisterRequest;
import com.lexiflow.auth.dto.RegisterResponse;
import com.lexiflow.auth.dto.UserBriefResponse;
import com.lexiflow.auth.service.AuthRateLimitService.LoginFailureStatus;
import com.lexiflow.auth.security.JwtTokenService;
import com.lexiflow.auth.security.JwtTokenService.TokenClaims;
import com.lexiflow.common.error.ErrorCode;
import com.lexiflow.common.exception.BizException;
import com.lexiflow.user.domain.User;
import com.lexiflow.user.domain.UserStatus;
import com.lexiflow.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 认证服务。
 *
 * <p>提供注册、登录、Token 刷新、退出登录等核心认证业务逻辑。
 * 登录时集成限流服务和验证码服务，支持登录失败计数与封禁机制。
 * Token 刷新时校验撤销状态、会话状态和版本号。</p>
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final AuthRateLimitService authRateLimitService;
    private final JwtRevocationService jwtRevocationService;
    private final RefreshTokenSessionService refreshTokenSessionService;
    private final TokenVersionService tokenVersionService;
    private final LoginCaptchaService loginCaptchaService;

    /** 用户注册，创建新用户并返回注册结果 */
    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        User user = userService.createUser(request.email(), request.password(), request.nickname());
        return new RegisterResponse(String.valueOf(user.getId()), user.getEmail(), user.getNickname());
    }

    /**
     * 用户登录。
     *
     * <p>校验限流状态和验证码，验证密码后生成 Access Token 和 Refresh Token。
     * 登录失败时记录失败次数，达到阈值时要求验证码或封禁。</p>
     *
     * @param request  登录请求
     * @param clientIp 客户端 IP
     * @return 登录结果，包含响应体、Refresh Token 及其 TTL
     */
    @Transactional
    public LoginResult login(LoginRequest request, String clientIp) {
        authRateLimitService.assertLoginAllowed(request.email(), clientIp);
        if (authRateLimitService.requiresCaptcha(request.email(), clientIp)) {
            loginCaptchaService.assertValid(request.captchaId(), request.captchaCode());
        }
        User user = userService.findByEmail(request.email());
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            LoginFailureStatus failureStatus = authRateLimitService.recordLoginFailure(request.email(), clientIp);
            if (failureStatus.blocked()) {
                throw new BizException(ErrorCode.TOO_MANY_REQUESTS, "登录失败次数过多，请稍后再试");
            }
            if (failureStatus.captchaRequired()) {
                throw new BizException(ErrorCode.LOGIN_CAPTCHA_REQUIRED, "邮箱或密码错误，请输入验证码后再试");
            }
            throw new BizException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BizException(ErrorCode.USER_DISABLED);
        }
        authRateLimitService.clearLoginFailures(request.email());
        userService.updateLoginInfo(user.getId(), clientIp);

        long tokenVersion = tokenVersionService.currentVersion(user.getId());
        String accessToken = jwtTokenService.createAccessToken(user, tokenVersion);
        String refreshToken = jwtTokenService.createRefreshToken(user, tokenVersion);
        refreshTokenSessionService.store(jwtTokenService.parseRefreshToken(refreshToken));
        LoginResponse response = new LoginResponse(
                accessToken,
                jwtTokenService.accessTokenTtlSeconds(),
                "csrf-token-placeholder",
                UserBriefResponse.from(user)
        );
        return new LoginResult(response, refreshToken, jwtTokenService.refreshTokenTtlSeconds());
    }

    /**
     * 刷新 Access Token。
     *
     * <p>校验 Refresh Token 的撤销状态、会话状态和版本号后，签发新的 Access Token。</p>
     *
     * @param refreshToken Refresh Token 字符串
     * @return 登录响应（包含新 Access Token）
     */
    public LoginResponse refresh(String refreshToken) {
        TokenClaims claims = jwtTokenService.parseRefreshToken(refreshToken);
        if (jwtRevocationService.isRefreshTokenRevoked(claims.tokenId())) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        if (refreshTokenSessionService.getStatus(claims) == RefreshTokenSessionStatus.MISSING) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        if (!tokenVersionService.isCurrent(claims)) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        User user = userService.getActiveUserById(claims.userId());
        return new LoginResponse(
                jwtTokenService.createAccessToken(user, claims.tokenVersion()),
                jwtTokenService.accessTokenTtlSeconds(),
                "csrf-token-placeholder",
                UserBriefResponse.from(user)
        );
    }

    /** 退出登录，同时撤销 Refresh Token 和 Access Token */
    public void logout(String refreshToken, String accessToken) {
        revokeRefreshToken(refreshToken);
        revokeAccessToken(accessToken);
    }

    /** 查询当前登录用户的摘要信息 */
    public UserBriefResponse currentUser(Long userId) {
        return UserBriefResponse.from(userService.getActiveUserById(userId));
    }

    /** 撤销 Refresh Token：从会话服务中删除并在撤销列表中标记 */
    private void revokeRefreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        try {
            TokenClaims claims = jwtTokenService.parseRefreshToken(refreshToken);
            refreshTokenSessionService.delete(claims.tokenId());
            jwtRevocationService.revokeRefreshToken(claims.tokenId(), claims.expiresAt());
        } catch (BizException ignored) {
            // 退出登录保持幂等，非法或过期 token 不影响清 Cookie。
        }
    }

    /** 撤销 Access Token：在撤销列表中标记 */
    private void revokeAccessToken(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            return;
        }
        try {
            TokenClaims claims = jwtTokenService.parseAccessToken(accessToken);
            jwtRevocationService.revokeAccessToken(claims.tokenId(), claims.expiresAt());
        } catch (BizException ignored) {
            // 退出登录保持幂等，非法或过期 token 不影响清 Cookie。
        }
    }

    /**
     * 登录结果记录。
     *
     * @param response           登录响应体
     * @param refreshToken       Refresh Token
     * @param refreshTokenTtlSeconds Refresh Token 有效期（秒）
     */
    public record LoginResult(LoginResponse response, String refreshToken, long refreshTokenTtlSeconds) {
    }
}
