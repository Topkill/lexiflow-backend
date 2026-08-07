package com.lexiflow.infra.redis;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

/**
 * Redis 分布式锁服务。
 * <p>
 * 基于 Redis SETNX 实现分布式锁，支持：
 * <ul>
 *   <li>尝试获取锁（带 TTL）</li>
 *   <li>检查锁状态</li>
 *   <li>释放锁（使用 Lua 脚本确保只释放自己持有的锁）</li>
 * </ul>
 * </p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RedisDistributedLockService {

    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class
    );

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 尝试获取分布式锁。
     *
     * @param key 锁的键名
     * @param ttl 锁的过期时间
     * @return 锁尝试结果（成功/被占用/不可用）
     */
    public RedisLockAttempt tryLock(String key, Duration ttl) {
        String ownerToken = UUID.randomUUID().toString();
        try {
            Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(key, ownerToken, ttl);
            if (Boolean.TRUE.equals(acquired)) {
                return RedisLockAttempt.acquired(new RedisLock(key, ownerToken));
            }
            return RedisLockAttempt.held(key);
        } catch (RuntimeException ex) {
            log.warn("Redis lock unavailable, key={}", key, ex);
            return RedisLockAttempt.unavailable(key);
        }
    }

    /** 检查指定键是否被锁定。 */
    public boolean isLocked(String key) {
        try {
            return Boolean.TRUE.equals(stringRedisTemplate.hasKey(key));
        } catch (RuntimeException ex) {
            log.warn("Redis lock status unavailable, key={}", key, ex);
            return false;
        }
    }

    /**
     * 释放分布式锁。
     * <p>
     * 使用 Lua 脚本原子性地检查并删除，确保只释放自己持有的锁。
     * </p>
     *
     * @param lock 要释放的锁
     * @return 是否成功释放
     */
    public boolean release(RedisLock lock) {
        if (lock == null || lock.ownerToken() == null) {
            return false;
        }
        try {
            Long released = stringRedisTemplate.execute(RELEASE_SCRIPT, List.of(lock.key()), lock.ownerToken());
            return Long.valueOf(1L).equals(released);
        } catch (RuntimeException ex) {
            log.warn("Redis lock release failed, key={}", lock.key(), ex);
            return false;
        }
    }
}
