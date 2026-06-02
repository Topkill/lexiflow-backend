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
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.Test;

class JwtTokenServiceTest {

    private static final String JWT_SECRET = "0123456789abcdef0123456789abcdef";

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
    void parseAccessTokenShouldRejectTokenWithoutJti() {
        JwtTokenService service = jwtTokenService();

        assertThatThrownBy(() -> service.parseAccessToken(legacyAccessTokenWithoutJti()))
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

    @Test
    void parseRefreshTokenShouldRejectTokenWithoutJti() {
        JwtTokenService service = jwtTokenService();

        assertThatThrownBy(() -> service.parseRefreshToken(legacyRefreshTokenWithoutJti()))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    private JwtTokenService jwtTokenService() {
        return new JwtTokenService(new JwtProperties(
                JWT_SECRET,
                15,
                7
        ));
    }

    private String legacyAccessTokenWithoutJti() {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject("7")
                .claim("email", "student@example.com")
                .claim("role", UserRole.USER.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    private String legacyRefreshTokenWithoutJti() {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject("7")
                .claim("type", "refresh")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
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
