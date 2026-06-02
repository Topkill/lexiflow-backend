package com.lexiflow.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lexiflow.auth.dto.LoginRequest;
import com.lexiflow.auth.security.JwtTokenService;
import com.lexiflow.auth.security.JwtTokenService.TokenClaims;
import com.lexiflow.common.error.ErrorCode;
import com.lexiflow.common.exception.BizException;
import com.lexiflow.user.domain.User;
import com.lexiflow.user.domain.UserRole;
import com.lexiflow.user.domain.UserStatus;
import com.lexiflow.user.service.UserService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserService userService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtTokenService jwtTokenService;
    @Mock
    private AuthRateLimitService authRateLimitService;
    @Mock
    private JwtRevocationService jwtRevocationService;
    @Mock
    private RefreshTokenSessionService refreshTokenSessionService;
    @Mock
    private TokenVersionService tokenVersionService;
    @Mock
    private LoginCaptchaService loginCaptchaService;

    @Test
    void loginFailureShouldRecordFailure() {
        AuthService service = authService();
        LoginRequest request = new LoginRequest("student@example.com", "bad-password");
        when(userService.findByEmail("student@example.com")).thenReturn(null);
        when(authRateLimitService.recordLoginFailure("student@example.com", "127.0.0.1"))
                .thenReturn(new AuthRateLimitService.LoginFailureStatus(false, false));

        assertThatThrownBy(() -> service.login(request, "127.0.0.1"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);

        verify(authRateLimitService).assertLoginAllowed("student@example.com", "127.0.0.1");
        verify(authRateLimitService).recordLoginFailure("student@example.com", "127.0.0.1");
        verify(authRateLimitService, never()).clearLoginFailures(anyString());
    }

    @Test
    void thirdLoginFailureShouldAskForCaptcha() {
        AuthService service = authService();
        LoginRequest request = new LoginRequest("student@example.com", "bad-password");
        when(userService.findByEmail("student@example.com")).thenReturn(null);
        when(authRateLimitService.recordLoginFailure("student@example.com", "127.0.0.1"))
                .thenReturn(new AuthRateLimitService.LoginFailureStatus(true, false));

        assertThatThrownBy(() -> service.login(request, "127.0.0.1"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.LOGIN_CAPTCHA_REQUIRED);
    }

    @Test
    void fifthLoginFailureShouldBlockTemporarily() {
        AuthService service = authService();
        LoginRequest request = new LoginRequest("student@example.com", "bad-password");
        when(userService.findByEmail("student@example.com")).thenReturn(null);
        when(authRateLimitService.recordLoginFailure("student@example.com", "127.0.0.1"))
                .thenReturn(new AuthRateLimitService.LoginFailureStatus(true, true));

        assertThatThrownBy(() -> service.login(request, "127.0.0.1"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.TOO_MANY_REQUESTS);
    }

    @Test
    void loginSuccessShouldClearFailuresAndReturnTokens() {
        AuthService service = authService();
        User user = activeUser();
        LoginRequest request = new LoginRequest("student@example.com", "Password123!");
        when(userService.findByEmail("student@example.com")).thenReturn(user);
        when(passwordEncoder.matches("Password123!", "hash")).thenReturn(true);
        when(tokenVersionService.currentVersion(7L)).thenReturn(3L);
        when(jwtTokenService.createAccessToken(user, 3L)).thenReturn("access-token");
        when(jwtTokenService.createRefreshToken(user, 3L)).thenReturn("refresh-token");
        TokenClaims refreshClaims = new TokenClaims(7L, "refresh-jti", Instant.now().plusSeconds(604800), 3L);
        when(jwtTokenService.parseRefreshToken("refresh-token")).thenReturn(refreshClaims);
        when(jwtTokenService.accessTokenTtlSeconds()).thenReturn(900L);
        when(jwtTokenService.refreshTokenTtlSeconds()).thenReturn(604800L);

        AuthService.LoginResult result = service.login(request, "127.0.0.1");

        assertThat(result.response().accessToken()).isEqualTo("access-token");
        assertThat(result.refreshToken()).isEqualTo("refresh-token");
        assertThat(result.refreshTokenTtlSeconds()).isEqualTo(604800L);
        verify(authRateLimitService).clearLoginFailures("student@example.com");
        verify(refreshTokenSessionService).store(refreshClaims);
        verify(userService).updateLoginInfo(7L, "127.0.0.1");
    }

    @Test
    void loginShouldRequireCaptchaWhenFailureThresholdReached() {
        AuthService service = authService();
        LoginRequest request = new LoginRequest("student@example.com", "Password123!");
        when(authRateLimitService.requiresCaptcha("student@example.com", "127.0.0.1")).thenReturn(true);
        doThrow(new BizException(ErrorCode.LOGIN_CAPTCHA_REQUIRED))
                .when(loginCaptchaService).assertValid(null, null);

        assertThatThrownBy(() -> service.login(request, "127.0.0.1"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.LOGIN_CAPTCHA_REQUIRED);

        verify(loginCaptchaService).assertValid(null, null);
        verify(userService, never()).findByEmail(anyString());
        verify(authRateLimitService, never()).recordLoginFailure(anyString(), anyString());
    }

    @Test
    void loginShouldContinueWhenCaptchaIsValid() {
        AuthService service = authService();
        User user = activeUser();
        LoginRequest request = new LoginRequest("student@example.com", "Password123!", "captcha-id", "1234");
        when(authRateLimitService.requiresCaptcha("student@example.com", "127.0.0.1")).thenReturn(true);
        when(userService.findByEmail("student@example.com")).thenReturn(user);
        when(passwordEncoder.matches("Password123!", "hash")).thenReturn(true);
        when(tokenVersionService.currentVersion(7L)).thenReturn(3L);
        when(jwtTokenService.createAccessToken(user, 3L)).thenReturn("access-token");
        when(jwtTokenService.createRefreshToken(user, 3L)).thenReturn("refresh-token");
        TokenClaims refreshClaims = new TokenClaims(7L, "refresh-jti", Instant.now().plusSeconds(604800), 3L);
        when(jwtTokenService.parseRefreshToken("refresh-token")).thenReturn(refreshClaims);
        when(jwtTokenService.accessTokenTtlSeconds()).thenReturn(900L);
        when(jwtTokenService.refreshTokenTtlSeconds()).thenReturn(604800L);

        AuthService.LoginResult result = service.login(request, "127.0.0.1");

        assertThat(result.response().accessToken()).isEqualTo("access-token");
        verify(loginCaptchaService).assertValid("captcha-id", "1234");
        verify(authRateLimitService).clearLoginFailures("student@example.com");
    }

    @Test
    void refreshShouldRejectRevokedRefreshToken() {
        AuthService service = authService();
        TokenClaims claims = new TokenClaims(7L, "refresh-jti", Instant.now().plusSeconds(60), 1L);
        when(jwtTokenService.parseRefreshToken("refresh-token")).thenReturn(claims);
        when(jwtRevocationService.isRefreshTokenRevoked("refresh-jti")).thenReturn(true);

        assertThatThrownBy(() -> service.refresh("refresh-token"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED);

        verify(userService, never()).getActiveUserById(anyLong());
    }

    @Test
    void refreshShouldRejectMissingRefreshSession() {
        AuthService service = authService();
        TokenClaims claims = new TokenClaims(7L, "refresh-jti", Instant.now().plusSeconds(60), 1L);
        when(jwtTokenService.parseRefreshToken("refresh-token")).thenReturn(claims);
        when(jwtRevocationService.isRefreshTokenRevoked("refresh-jti")).thenReturn(false);
        when(refreshTokenSessionService.getStatus(claims)).thenReturn(RefreshTokenSessionStatus.MISSING);

        assertThatThrownBy(() -> service.refresh("refresh-token"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED);

        verify(userService, never()).getActiveUserById(anyLong());
    }

    @Test
    void refreshShouldRejectTokenWhenParsingFails() {
        AuthService service = authService();
        when(jwtTokenService.parseRefreshToken("legacy-refresh"))
                .thenThrow(new BizException(ErrorCode.UNAUTHORIZED));

        assertThatThrownBy(() -> service.refresh("legacy-refresh"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED);

        verifyNoInteractions(jwtRevocationService);
        verifyNoInteractions(refreshTokenSessionService);
        verify(userService, never()).getActiveUserById(anyLong());
        verify(jwtTokenService, never()).createAccessToken(any(User.class), anyLong());
    }

    @Test
    void refreshShouldRejectStaleTokenVersion() {
        AuthService service = authService();
        TokenClaims claims = new TokenClaims(7L, "refresh-jti", Instant.now().plusSeconds(60), 1L);
        when(jwtTokenService.parseRefreshToken("refresh-token")).thenReturn(claims);
        when(jwtRevocationService.isRefreshTokenRevoked("refresh-jti")).thenReturn(false);
        when(refreshTokenSessionService.getStatus(claims)).thenReturn(RefreshTokenSessionStatus.ACTIVE);
        when(tokenVersionService.isCurrent(claims)).thenReturn(false);

        assertThatThrownBy(() -> service.refresh("refresh-token"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED);

        verify(userService, never()).getActiveUserById(anyLong());
        verify(jwtTokenService, never()).createAccessToken(any(User.class), anyLong());
    }

    @Test
    void refreshShouldFallbackWhenRefreshSessionStoreUnavailable() {
        AuthService service = authService();
        User user = activeUser();
        TokenClaims claims = new TokenClaims(7L, "refresh-jti", Instant.now().plusSeconds(60), 3L);
        when(jwtTokenService.parseRefreshToken("refresh-token")).thenReturn(claims);
        when(jwtRevocationService.isRefreshTokenRevoked("refresh-jti")).thenReturn(false);
        when(refreshTokenSessionService.getStatus(claims)).thenReturn(RefreshTokenSessionStatus.UNAVAILABLE);
        when(tokenVersionService.isCurrent(claims)).thenReturn(true);
        when(userService.getActiveUserById(7L)).thenReturn(user);
        when(jwtTokenService.createAccessToken(user, 3L)).thenReturn("new-access-token");
        when(jwtTokenService.accessTokenTtlSeconds()).thenReturn(900L);

        assertThat(service.refresh("refresh-token").accessToken()).isEqualTo("new-access-token");
    }

    @Test
    void logoutShouldRevokeParsedRefreshAndAccessTokens() {
        AuthService service = authService();
        Instant refreshExpiresAt = Instant.now().plusSeconds(3600);
        Instant accessExpiresAt = Instant.now().plusSeconds(900);
        when(jwtTokenService.parseRefreshToken("refresh-token"))
                .thenReturn(new TokenClaims(7L, "refresh-jti", refreshExpiresAt, 1L));
        when(jwtTokenService.parseAccessToken("access-token"))
                .thenReturn(new TokenClaims(7L, "access-jti", accessExpiresAt, 1L));

        service.logout("refresh-token", "access-token");

        verify(refreshTokenSessionService).delete("refresh-jti");
        verify(jwtRevocationService).revokeRefreshToken("refresh-jti", refreshExpiresAt);
        verify(jwtRevocationService).revokeAccessToken("access-jti", accessExpiresAt);
    }

    @Test
    void logoutShouldIgnoreInvalidTokens() {
        AuthService service = authService();
        when(jwtTokenService.parseRefreshToken("bad-refresh"))
                .thenThrow(new BizException(ErrorCode.UNAUTHORIZED));
        when(jwtTokenService.parseAccessToken("bad-access"))
                .thenThrow(new BizException(ErrorCode.UNAUTHORIZED));

        service.logout("bad-refresh", "bad-access");

        verifyNoInteractions(jwtRevocationService);
        verifyNoInteractions(refreshTokenSessionService);
    }

    private AuthService authService() {
        return new AuthService(
                userService,
                passwordEncoder,
                jwtTokenService,
                authRateLimitService,
                jwtRevocationService,
                refreshTokenSessionService,
                tokenVersionService,
                loginCaptchaService
        );
    }

    private User activeUser() {
        User user = new User();
        user.setId(7L);
        user.setEmail("student@example.com");
        user.setPasswordHash("hash");
        user.setNickname("Student");
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }
}
