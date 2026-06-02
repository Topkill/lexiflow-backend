package com.lexiflow.auth.service;

import com.lexiflow.auth.dto.LoginRequest;
import com.lexiflow.auth.dto.LoginResponse;
import com.lexiflow.auth.dto.RegisterRequest;
import com.lexiflow.auth.dto.RegisterResponse;
import com.lexiflow.auth.dto.UserBriefResponse;
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

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final AuthRateLimitService authRateLimitService;
    private final JwtRevocationService jwtRevocationService;
    private final RefreshTokenSessionService refreshTokenSessionService;

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        User user = userService.createUser(request.email(), request.password(), request.nickname());
        return new RegisterResponse(String.valueOf(user.getId()), user.getEmail(), user.getNickname());
    }

    @Transactional
    public LoginResult login(LoginRequest request, String clientIp) {
        authRateLimitService.assertLoginAllowed(request.email(), clientIp);
        User user = userService.findByEmail(request.email());
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            authRateLimitService.recordLoginFailure(request.email(), clientIp);
            throw new BizException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BizException(ErrorCode.USER_DISABLED);
        }
        authRateLimitService.clearLoginFailures(request.email());
        userService.updateLoginInfo(user.getId(), clientIp);

        String accessToken = jwtTokenService.createAccessToken(user);
        String refreshToken = jwtTokenService.createRefreshToken(user);
        refreshTokenSessionService.store(jwtTokenService.parseRefreshToken(refreshToken));
        LoginResponse response = new LoginResponse(
                accessToken,
                jwtTokenService.accessTokenTtlSeconds(),
                "csrf-token-placeholder",
                UserBriefResponse.from(user)
        );
        return new LoginResult(response, refreshToken, jwtTokenService.refreshTokenTtlSeconds());
    }

    public LoginResponse refresh(String refreshToken) {
        TokenClaims claims = jwtTokenService.parseRefreshToken(refreshToken);
        if (jwtRevocationService.isRefreshTokenRevoked(claims.tokenId())) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        if (refreshTokenSessionService.getStatus(claims) == RefreshTokenSessionStatus.MISSING) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        User user = userService.getActiveUserById(claims.userId());
        return new LoginResponse(
                jwtTokenService.createAccessToken(user),
                jwtTokenService.accessTokenTtlSeconds(),
                "csrf-token-placeholder",
                UserBriefResponse.from(user)
        );
    }

    public void logout(String refreshToken, String accessToken) {
        revokeRefreshToken(refreshToken);
        revokeAccessToken(accessToken);
    }

    public UserBriefResponse currentUser(Long userId) {
        return UserBriefResponse.from(userService.getActiveUserById(userId));
    }

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

    public record LoginResult(LoginResponse response, String refreshToken, long refreshTokenTtlSeconds) {
    }
}
