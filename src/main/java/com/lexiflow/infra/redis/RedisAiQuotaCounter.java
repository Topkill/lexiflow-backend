package com.lexiflow.infra.redis;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

/**
 * Redis AI 每日配额计数器服务。
 * <p>
 * 使用 Redis 原子操作实现用户每日 AI 调用配额的预留和计数。
 * 支持两种模式：
 * <ul>
 *   <li>普通预留：在已初始化的计数器上预留配额</li>
 *   <li>初始化并预留：首次使用时初始化计数器并设置数据库基线已用量</li>
 * </ul>
 * 所有操作通过 Lua 脚本保证原子性。
 * </p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RedisAiQuotaCounter {

    private static final DefaultRedisScript<Long> RESERVE_QUOTA_SCRIPT = new DefaultRedisScript<>(
            """
                    local current = redis.call('GET', KEYS[1])
                    local quota = tonumber(ARGV[1])
                    if not current then
                        return -2
                    end
                    if current and tonumber(current) >= quota then
                        return -1
                    end
                    local used = redis.call('INCR', KEYS[1])
                    if redis.call('PTTL', KEYS[1]) < 0 then
                        redis.call('PEXPIRE', KEYS[1], ARGV[2])
                    end
                    if used > quota then
                        return -1
                    end
                    return used
                    """,
            Long.class
    );

    private static final DefaultRedisScript<Long> INITIALIZE_AND_RESERVE_QUOTA_SCRIPT = new DefaultRedisScript<>(
            """
                    local current = redis.call('GET', KEYS[1])
                    local quota = tonumber(ARGV[1])
                    local ttlMillis = ARGV[2]
                    local baselineUsed = tonumber(ARGV[3])
                    if current then
                        if tonumber(current) >= quota then
                            return -1
                        end
                        local used = redis.call('INCR', KEYS[1])
                        if redis.call('PTTL', KEYS[1]) < 0 then
                            redis.call('PEXPIRE', KEYS[1], ttlMillis)
                        end
                        if used > quota then
                            return -1
                        end
                        return used
                    end
                    if baselineUsed >= quota then
                        redis.call('SET', KEYS[1], baselineUsed, 'PX', ttlMillis)
                        return -1
                    end
                    local used = baselineUsed + 1
                    redis.call('SET', KEYS[1], used, 'PX', ttlMillis)
                    return used
                    """,
            Long.class
    );

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 预留用户每日 AI 调用配额。
     *
     * @param userId 用户 ID
     * @param quota  每日配额上限
     * @param date   当前日期
     * @param ttl    缓存过期时间
     * @return 配额预留结果
     */
    public RedisQuotaReserveResult reserveDailyQuota(Long userId, int quota, LocalDate date, Duration ttl) {
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            return RedisQuotaReserveResult.unavailableResult();
        }
        String key = RedisKeys.aiQuotaKey(date, userId);
        try {
            Long used = stringRedisTemplate.execute(
                    RESERVE_QUOTA_SCRIPT,
                    List.of(key),
                    String.valueOf(quota),
                    String.valueOf(ttl.toMillis())
            );
            if (used == null) {
                return RedisQuotaReserveResult.unavailableResult();
            }
            if (used == -2L) {
                return RedisQuotaReserveResult.initializationRequiredResult();
            }
            if (used < 0) {
                return RedisQuotaReserveResult.exhaustedResult();
            }
            return RedisQuotaReserveResult.allowed(used);
        } catch (RuntimeException ex) {
            log.warn("Redis AI quota counter unavailable, key={}", key, ex);
            return RedisQuotaReserveResult.unavailableResult();
        }
    }

    /**
     * 初始化并预留用户每日 AI 调用配额。
     * <p>
     * 首次使用时，将数据库中的已用量作为基线初始化 Redis 计数器。
     * </p>
     *
     * @param userId       用户 ID
     * @param quota        每日配额上限
     * @param date         当前日期
     * @param ttl          缓存过期时间
     * @param baselineUsed 数据库中的已用量基线
     * @return 配额预留结果
     */
    public RedisQuotaReserveResult initializeAndReserveDailyQuota(Long userId, int quota, LocalDate date, Duration ttl, long baselineUsed) {
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            return RedisQuotaReserveResult.unavailableResult();
        }
        String key = RedisKeys.aiQuotaKey(date, userId);
        try {
            Long used = stringRedisTemplate.execute(
                    INITIALIZE_AND_RESERVE_QUOTA_SCRIPT,
                    List.of(key),
                    String.valueOf(quota),
                    String.valueOf(ttl.toMillis()),
                    String.valueOf(Math.max(0, baselineUsed))
            );
            if (used == null) {
                return RedisQuotaReserveResult.unavailableResult();
            }
            if (used < 0) {
                return RedisQuotaReserveResult.exhaustedResult();
            }
            return RedisQuotaReserveResult.allowed(used);
        } catch (RuntimeException ex) {
            log.warn("Redis AI quota counter initialization unavailable, key={}", key, ex);
            return RedisQuotaReserveResult.unavailableResult();
        }
    }
}
