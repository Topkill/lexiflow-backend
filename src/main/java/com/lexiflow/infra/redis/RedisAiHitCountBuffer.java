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

    private long drainField(String key, String field, long requested) {
        Long drained = stringRedisTemplate.execute(
                DRAIN_FIELD_SCRIPT,
                List.of(key),
                field,
                String.valueOf(requested)
        );
        return drained == null ? 0 : drained;
    }

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

    private long safePositive(Long value) {
        return value == null || value <= 0 ? 0 : value;
    }
}
