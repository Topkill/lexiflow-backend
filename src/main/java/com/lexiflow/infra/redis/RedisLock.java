package com.lexiflow.infra.redis;

/**
 * Redis 分布式锁记录。
 *
 * @param key        锁的 Redis 键名
 * @param ownerToken 锁持有者的唯一标识（UUID）
 */
public record RedisLock(String key, String ownerToken) {
}
