package com.lexiflow.auth.security;

import com.lexiflow.user.domain.User;
import com.lexiflow.user.domain.UserRole;
import com.lexiflow.user.domain.UserStatus;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * 认证用户对象，实现 Spring Security 的 {@link UserDetails} 接口。
 *
 * <p>作为 SecurityContext 中的 Principal，承载当前登录用户的核心信息。
 * 通过 {@link #from(User)} 静态工厂方法从 User 实体创建。</p>
 *
 * @param id       用户 ID
 * @param email    邮箱
 * @param nickname 昵称
 * @param role     用户角色
 * @param status   用户状态
 */
public record AuthUser(
        Long id,
        String email,
        String nickname,
        UserRole role,
        UserStatus status
) implements UserDetails {

    /** 从 User 实体创建 AuthUser */
    public static AuthUser from(User user) {
        return new AuthUser(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getRole(),
                user.getStatus()
        );
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return "";
    }

    @Override
    public String getUsername() {
        return String.valueOf(id);
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return status != UserStatus.LOCKED;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return status == UserStatus.ACTIVE;
    }
}
