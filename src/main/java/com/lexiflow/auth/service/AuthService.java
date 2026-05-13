package com.lexiflow.auth.service;

import com.lexiflow.auth.dto.LoginRequest;
import com.lexiflow.auth.dto.LoginResponse;
import com.lexiflow.auth.dto.RegisterRequest;
import com.lexiflow.auth.dto.RegisterResponse;
import com.lexiflow.auth.dto.UserBriefResponse;
import com.lexiflow.auth.security.JwtTokenService;
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

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        User user = userService.createUser(request.email(), request.password(), request.nickname());
        return new RegisterResponse(String.valueOf(user.getId()), user.getEmail(), user.getNickname());
    }

    @Transactional
    public LoginResult login(LoginRequest request, String clientIp) {
        User user = userService.findByEmail(request.email());
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BizException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BizException(ErrorCode.USER_DISABLED);
        }
        userService.updateLoginInfo(user.getId(), clientIp);

        String accessToken = jwtTokenService.createAccessToken(user);
        String refreshToken = jwtTokenService.createRefreshToken(user);
        LoginResponse response = new LoginResponse(
                accessToken,
                jwtTokenService.accessTokenTtlSeconds(),
                "csrf-token-placeholder",
                UserBriefResponse.from(user)
        );
        return new LoginResult(response, refreshToken, jwtTokenService.refreshTokenTtlSeconds());
    }

    public LoginResponse refresh(String refreshToken) {
        Long userId = jwtTokenService.parseRefreshUserId(refreshToken);
        User user = userService.getActiveUserById(userId);
        return new LoginResponse(
                jwtTokenService.createAccessToken(user),
                jwtTokenService.accessTokenTtlSeconds(),
                "csrf-token-placeholder",
                UserBriefResponse.from(user)
        );
    }

    public UserBriefResponse currentUser(Long userId) {
        return UserBriefResponse.from(userService.getActiveUserById(userId));
    }

    public record LoginResult(LoginResponse response, String refreshToken, long refreshTokenTtlSeconds) {
    }
}
