package com.lexiflow.auth.security;

import com.lexiflow.auth.security.JwtTokenService.TokenClaims;
import com.lexiflow.auth.service.AuthUserCacheService;
import com.lexiflow.auth.service.JwtRevocationService;
import com.lexiflow.auth.service.TokenVersionService;
import com.lexiflow.user.domain.User;
import com.lexiflow.user.service.UserService;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenService jwtTokenService;
    private final JwtRevocationService jwtRevocationService;
    private final AuthUserCacheService authUserCacheService;
    private final TokenVersionService tokenVersionService;
    private final UserService userService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        DispatcherType dispatcherType = request.getDispatcherType();
        return dispatcherType == DispatcherType.ERROR || dispatcherType == DispatcherType.ASYNC;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = resolveToken(request);
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                TokenClaims claims = jwtTokenService.parseAccessToken(token);
                if (jwtRevocationService.isAccessTokenRevoked(claims.tokenId())) {
                    filterChain.doFilter(request, response);
                    return;
                }
                if (!tokenVersionService.isCurrent(claims)) {
                    filterChain.doFilter(request, response);
                    return;
                }
                AuthUser authUser = authUserCacheService.get(claims.userId());
                if (authUser == null) {
                    User user = userService.getActiveUserById(claims.userId());
                    authUser = AuthUser.from(user);
                    authUserCacheService.put(authUser);
                }
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        authUser,
                        null,
                        authUser.getAuthorities()
                );
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (RuntimeException ignored) {
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (!StringUtils.hasText(authorization) || !authorization.startsWith(BEARER_PREFIX)) {
            return null;
        }
        return authorization.substring(BEARER_PREFIX.length());
    }
}
