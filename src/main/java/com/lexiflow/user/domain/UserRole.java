package com.lexiflow.user.domain;

/**
 * 用户角色枚举。
 *
 * <p>决定用户在系统中的权限范围，与 Spring Security 权限体系配合使用。</p>
 */
public enum UserRole {
    /** 普通用户，拥有学习、复习、测验等基础功能权限 */
    USER,
    /** 管理员，拥有用户管理、词库管理、数据统计等后台管理权限 */
    ADMIN
}
