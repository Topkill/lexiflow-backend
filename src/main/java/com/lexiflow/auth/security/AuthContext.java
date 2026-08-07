package com.lexiflow.auth.security;

import com.lexiflow.common.error.ErrorCode;
import com.lexiflow.common.exception.BizException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 认证上下文工具类。
 *
 * <p>基于 Spring Security 的 {@link SecurityContextHolder} 提供当前登录用户的快捷访问方法。
 * 当未认证或认证信息异常时抛出 {@link BizException} UNAUTHORIZED 异常。</p>
 */
public final class AuthContext {

    private AuthContext() {
    }

    /** 获取当前登录的认证用户对象 */
    public static AuthUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthUser authUser)) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return authUser;
    }

    /** 获取当前登录用户的 ID */
    public static Long currentUserId() {
        return currentUser().id();
    }
}
