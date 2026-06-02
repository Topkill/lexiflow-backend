package com.lexiflow.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lexiflow.auth.security.JwtTokenService.TokenClaims;
import com.lexiflow.auth.service.JwtRevocationService;
import com.lexiflow.user.domain.User;
import com.lexiflow.user.domain.UserRole;
import com.lexiflow.user.domain.UserStatus;
import com.lexiflow.user.service.UserService;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtTokenService jwtTokenService;
    @Mock
    private JwtRevocationService jwtRevocationService;
    @Mock
    private UserService userService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void revokedAccessTokenShouldSkipAuthentication() throws Exception {
        JwtAuthenticationFilter filter = jwtAuthenticationFilter();
        MockHttpServletRequest request = requestWithBearer("access-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        when(jwtTokenService.parseAccessToken("access-token"))
                .thenReturn(new TokenClaims(7L, "access-jti", Instant.now().plusSeconds(60)));
        when(jwtRevocationService.isAccessTokenRevoked("access-jti")).thenReturn(true);

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(userService, never()).getActiveUserById(anyLong());
    }

    @Test
    void validAccessTokenShouldSetAuthentication() throws Exception {
        JwtAuthenticationFilter filter = jwtAuthenticationFilter();
        MockHttpServletRequest request = requestWithBearer("access-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        User user = activeUser();
        when(jwtTokenService.parseAccessToken("access-token"))
                .thenReturn(new TokenClaims(7L, "access-jti", Instant.now().plusSeconds(60)));
        when(jwtRevocationService.isAccessTokenRevoked("access-jti")).thenReturn(false);
        when(userService.getActiveUserById(7L)).thenReturn(user);

        filter.doFilter(request, response, chain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isInstanceOf(AuthUser.class);
        assertThat(((AuthUser) authentication.getPrincipal()).id()).isEqualTo(7L);
    }

    private JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter(jwtTokenService, jwtRevocationService, userService);
    }

    private MockHttpServletRequest requestWithBearer(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        return request;
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
