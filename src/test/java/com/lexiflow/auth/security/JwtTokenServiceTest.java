package com.lexiflow.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lexiflow.auth.security.JwtTokenService.TokenClaims;
import com.lexiflow.common.error.ErrorCode;
import com.lexiflow.common.exception.BizException;
import com.lexiflow.infra.properties.JwtProperties;
import com.lexiflow.user.domain.User;
import com.lexiflow.user.domain.UserRole;
import com.lexiflow.user.domain.UserStatus;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class JwtTokenServiceTest {

    @Test
    void accessTokenShouldIncludeJtiAndParseClaims() {
        JwtTokenService service = jwtTokenService();

        TokenClaims claims = service.parseAccessToken(service.createAccessToken(activeUser()));

        assertThat(claims.userId()).isEqualTo(7L);
        assertThat(claims.tokenId()).isNotBlank();
        assertThat(claims.expiresAt()).isAfter(Instant.now());
    }

    @Test
    void parseAccessTokenShouldRejectRefreshToken() {
        JwtTokenService service = jwtTokenService();
        String refreshToken = service.createRefreshToken(activeUser());

        assertThatThrownBy(() -> service.parseAccessToken(refreshToken))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    void refreshTokenShouldIncludeJtiAndParseClaims() {
        JwtTokenService service = jwtTokenService();

        TokenClaims claims = service.parseRefreshToken(service.createRefreshToken(activeUser()));

        assertThat(claims.userId()).isEqualTo(7L);
        assertThat(claims.tokenId()).isNotBlank();
        assertThat(claims.expiresAt()).isAfter(Instant.now());
    }

    private JwtTokenService jwtTokenService() {
        return new JwtTokenService(new JwtProperties(
                "0123456789abcdef0123456789abcdef",
                15,
                7
        ));
    }

    private User activeUser() {
        User user = new User();
        user.setId(7L);
        user.setEmail("student@example.com");
        user.setNickname("Student");
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }
}
