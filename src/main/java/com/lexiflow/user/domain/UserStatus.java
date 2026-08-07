package com.lexiflow.user.domain;

/**
 * 用户状态枚举。
 *
 * <p>用于标识用户账号当前所处的状态，影响登录权限与功能可用性。</p>
 */
public enum UserStatus {
    /** 正常激活状态，可正常使用所有功能 */
    ACTIVE,
    /** 已禁用，管理员手动禁用该账号 */
    DISABLED,
    /** 已锁定，通常因安全原因（如多次登录失败）自动锁定 */
    LOCKED
}
