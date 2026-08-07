package com.lexiflow.auth.service;

/**
 * Refresh Token 会话状态枚举。
 *
 * <p>用于描述 Refresh Token 在 Redis 中的会话状态。</p>
 */
public enum RefreshTokenSessionStatus {
    /** 会话有效，Token 可用 */
    ACTIVE,
    /** 会话不存在，Token 已失效或被撤销 */
    MISSING,
    /** Redis 不可用，无法确认会话状态 */
    UNAVAILABLE
}
