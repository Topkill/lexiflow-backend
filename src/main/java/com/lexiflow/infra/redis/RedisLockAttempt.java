package com.lexiflow.infra.redis;

public record RedisLockAttempt(RedisLock lock, boolean acquired, boolean unavailable) {

    public static RedisLockAttempt acquired(RedisLock lock) {
        return new RedisLockAttempt(lock, true, false);
    }

    public static RedisLockAttempt held(String key) {
        return new RedisLockAttempt(new RedisLock(key, null), false, false);
    }

    public static RedisLockAttempt unavailable(String key) {
        return new RedisLockAttempt(new RedisLock(key, null), false, true);
    }
}
