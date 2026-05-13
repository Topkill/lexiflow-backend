package com.lexiflow.auth.security;

import com.lexiflow.common.error.ErrorCode;
import com.lexiflow.common.exception.BizException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class AuthContext {

    private AuthContext() {
    }

    public static AuthUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthUser authUser)) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return authUser;
    }

    public static Long currentUserId() {
        return currentUser().id();
    }
}
