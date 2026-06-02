package com.lexiflow.infra.redis;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class RedisDistributedLockService {

    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class
    );

    private final StringRedisTemplate stringRedisTemplate;

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

    public boolean isLocked(String key) {
        try {
            return Boolean.TRUE.equals(stringRedisTemplate.hasKey(key));
        } catch (RuntimeException ex) {
            log.warn("Redis lock status unavailable, key={}", key, ex);
            return false;
        }
    }

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
