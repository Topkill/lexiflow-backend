package com.lexiflow.infra.redis;

/**
 * Redis 分布式锁尝试结果。
 * <p>
 * 表示锁尝试的三种状态：
 * <ul>
 *   <li>成功获取（acquired = true）</li>
 *   <li>已被其他持有者占用（acquired = false, unavailable = false）</li>
 *   <li>Redis 不可用（unavailable = true）</li>
 * </ul>
 * </p>
 *
 * @param lock        锁信息
 * @param acquired    是否成功获取锁
 * @param unavailable Redis 是否不可用
 */
public record RedisLockAttempt(RedisLock lock, boolean acquired, boolean unavailable) {

    /** 创建成功获取锁的结果。 */
    public static RedisLockAttempt acquired(RedisLock lock) {
        return new RedisLockAttempt(lock, true, false);
    }

    /** 创建锁已被占用的结果。 */
    public static RedisLockAttempt held(String key) {
        return new RedisLockAttempt(new RedisLock(key, null), false, false);
    }

    /** 创建 Redis 不可用的结果。 */
    public static RedisLockAttempt unavailable(String key) {
        return new RedisLockAttempt(new RedisLock(key, null), false, true);
    }
}
