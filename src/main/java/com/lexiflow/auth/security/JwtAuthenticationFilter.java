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
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
/**
 * JWT 认证过滤器。
 * <p>
 * 继承自 {@link OncePerRequestFilter}，确保每个请求只执行一次过滤。
 * 主要负责从请求头中提取 JWT，验证其有效性、是否被撤销以及版本是否最新，
 * 并将认证信息设置到 Spring Security 的上下文中。
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** Bearer Token 前缀 */
    private static final String BEARER_PREFIX = "Bearer ";

    /** JWT 令牌服务，用于解析和验证 Token */
    private final JwtTokenService jwtTokenService;
    /** JWT 撤销服务，用于检查 Token 是否已被主动撤销（如用户登出） */
    private final JwtRevocationService jwtRevocationService;
    /** 认证用户缓存服务，用于缓存和快速获取用户信息，减少数据库查询 */
    private final AuthUserCacheService authUserCacheService;
    /** Token 版本服务，用于校验 Token 版本是否与当前用户状态一致（如密码修改后旧 Token 失效） */
    private final TokenVersionService tokenVersionService;
    /** 用户服务，用于在缓存未命中时从数据库加载用户信息 */
    private final UserService userService;

    /**
     * 判断当前请求是否不需要经过此过滤器。
     * <p>
     * 对于错误分发（ERROR）和异步分发（ASYNC）的请求，直接跳过过滤。
     *
     * @param request 当前 HTTP 请求
     * @return 如果请求类型为 ERROR 或 ASYNC 则返回 true，否则返回 false
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        DispatcherType dispatcherType = request.getDispatcherType();
        return dispatcherType == DispatcherType.ERROR || dispatcherType == DispatcherType.ASYNC;
    }

    /**
     * 执行内部过滤逻辑，处理 JWT 认证。
     *
     * @param request     当前 HTTP 请求
     * @param response    当前 HTTP 响应
     * @param filterChain 过滤器链
     * @throws ServletException 如果发生 Servlet 相关异常
     * @throws IOException      如果发生 I/O 异常
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        // 从请求头中解析 JWT Token
        String token = resolveToken(request);
        
        // 如果 Token 存在且当前 SecurityContext 中尚未设置认证信息
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                // 解析 Token 获取声明信息
                TokenClaims claims = jwtTokenService.parseAccessToken(token);
                
                // 检查 Token 是否已被撤销（例如用户主动登出）
                if (jwtRevocationService.isAccessTokenRevoked(claims.tokenId())) {
                    filterChain.doFilter(request, response);
                    return;
                }
                
                // 检查 Token 版本是否为最新（例如用户修改密码后，旧版本的 Token 应失效）
                if (!tokenVersionService.isCurrent(claims)) {
                    filterChain.doFilter(request, response);
                    return;
                }
                
                // 尝试从缓存中获取认证用户信息
                AuthUser authUser = authUserCacheService.get(claims.userId());
                if (authUser == null) {
                    // 缓存未命中，从数据库查询活跃用户并转换为 AuthUser
                    User user = userService.getActiveUserById(claims.userId());
                    authUser = AuthUser.from(user);
                    // 将用户信息放入缓存，供后续请求使用
                    authUserCacheService.put(authUser);
                }
                
                // 构建 Spring Security 的认证对象
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        authUser,
                        null, // 凭证（如密码）在 Token 认证中不需要
                        authUser.getAuthorities() // 用户权限集合
                );
                // 设置请求的详细信息（如 IP、SessionId 等）
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                // 将认证对象设置到 SecurityContext 中
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (RuntimeException ignored) {
                // 如果解析或验证过程中发生任何运行时异常，清除安全上下文，视为未认证
                SecurityContextHolder.clearContext();
            }
        }
        // 继续执行过滤器链
        filterChain.doFilter(request, response);
    }

    /**
     * 从 HTTP 请求的 Authorization 头中解析并提取 JWT Token。
     *
     * @param request 当前 HTTP 请求
     * @return 提取到的 JWT Token 字符串；如果请求头不存在或格式不正确，则返回 null
     */
    private String resolveToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        // 检查 Authorization 头是否存在且以 "Bearer " 开头
        if (!StringUtils.hasText(authorization) || !authorization.startsWith(BEARER_PREFIX)) {
            return null;
        }
        // 截取 "Bearer " 之后的部分作为实际的 Token
        return authorization.substring(BEARER_PREFIX.length());
    }
}
