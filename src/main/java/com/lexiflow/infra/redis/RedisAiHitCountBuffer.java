package com.lexiflow.infra.redis;

import com.lexiflow.ai.content.domain.AiContentType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

/**
 * Redis AI 调用计数缓冲区服务。
 * <p>
 * 使用 Redis Hash 结构缓冲 AI 内容调用次数，支持：
 * <ul>
 *   <li>增量计数：为指定内容结果 ID 累加调用次数</li>
 *   <li>批量排空：原子性地读取并清除缓冲区中的计数</li>
 *   <li>重入队列：将已排空的计数重新放回缓冲区（用于失败重试）</li>
 * </ul>
 * </p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RedisAiHitCountBuffer {

    private static final DefaultRedisScript<Long> DRAIN_FIELD_SCRIPT = new DefaultRedisScript<>(
            """
                    local current = tonumber(redis.call('HGET', KEYS[1], ARGV[1]) or '0')
                    local requested = tonumber(ARGV[2])
                    local drained = math.min(current, requested)
                    if drained <= 0 then
                        return 0
                    end
                    local remain = current - drained
                    if remain > 0 then
                        redis.call('HSET', KEYS[1], ARGV[1], remain)
                    else
                        redis.call('HDEL', KEYS[1], ARGV[1])
                    end
                    return drained
                    """,
            Long.class
    );

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 增加指定内容结果 ID 的调用计数。
     *
     * @param contentType AI 内容类型
     * @param resultId    内容结果 ID
     * @return 是否成功
     */
    public boolean incrementHit(AiContentType contentType, Long resultId) {
        if (contentType == null || resultId == null) {
            return false;
        }
        String key = RedisKeys.aiHitCountHashKey(contentType);
        try {
            stringRedisTemplate.opsForHash().increment(key, String.valueOf(resultId), 1);
            return true;
        } catch (RuntimeException ex) {
            log.warn("Redis AI hit buffer increment failed, key={}, resultId={}", key, resultId, ex);
            return false;
        }
    }

    /**
     * 排空指定内容类型的所有调用计数。
     * <p>
     * 使用 Lua 脚本原子性地读取并清除计数，保证并发安全。
     * </p>
     *
     * @param contentType AI 内容类型
     * @return 结果 ID 到调用次数的映射
     */
    public Map<Long, Long> drainHits(AiContentType contentType) {
        if (contentType == null) {
            return Map.of();
        }
        String key = RedisKeys.aiHitCountHashKey(contentType);
        try {
            Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(key);
            if (entries.isEmpty()) {
                return Map.of();
            }
            Map<Long, Long> drainedHits = new LinkedHashMap<>();
            for (Map.Entry<Object, Object> entry : entries.entrySet()) {
                Long resultId = parseLong(entry.getKey());
                long requested = safePositive(parseLong(entry.getValue()));
                if (resultId == null || requested <= 0) {
                    continue;
                }
                long drained = drainField(key, String.valueOf(resultId), requested);
                if (drained > 0) {
                    drainedHits.put(resultId, drained);
                }
            }
            return drainedHits;
        } catch (RuntimeException ex) {
            log.warn("Redis AI hit buffer drain failed, key={}", key, ex);
            return Map.of();
        }
    }

    /**
     * 将已排空的计数重新放回缓冲区。
     * <p>
     * 用于消息消费失败时的重试场景。
     * </p>
     *
     * @param contentType AI 内容类型
     * @param resultId    内容结果 ID
     * @param delta       要重新入队的计数
     */
    public void requeueHits(AiContentType contentType, Long resultId, long delta) {
        if (contentType == null || resultId == null || delta <= 0) {
            return;
        }
        String key = RedisKeys.aiHitCountHashKey(contentType);
        try {
            stringRedisTemplate.opsForHash().increment(key, String.valueOf(resultId), delta);
        } catch (RuntimeException ex) {
            log.warn("Redis AI hit buffer requeue failed, key={}, resultId={}, delta={}", key, resultId, delta, ex);
        }
    }

    /** 使用 Lua 脚本原子性地排空指定字段的计数。 */
    private long drainField(String key, String field, long requested) {
        Long drained = stringRedisTemplate.execute(
                DRAIN_FIELD_SCRIPT,
                List.of(key),
                field,
                String.valueOf(requested)
        );
        return drained == null ? 0 : drained;
    }

    /** 安全地将对象转换为 Long。 */
    private Long parseLong(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /** 确保返回值为正数，否则返回 0。 */
    private long safePositive(Long value) {
        return value == null || value <= 0 ? 0 : value;
    }
}
