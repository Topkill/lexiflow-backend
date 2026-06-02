package com.lexiflow.infra.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class RedisJsonCacheService {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public <T> T get(String key, Class<T> valueType) {
        try {
            String json = stringRedisTemplate.opsForValue().get(key);
            if (json == null || json.isBlank()) {
                return null;
            }
            return objectMapper.readValue(json, valueType);
        } catch (RuntimeException ex) {
            log.warn("Redis json cache unavailable, key={}", key, ex);
            return null;
        } catch (Exception ex) {
            log.warn("Redis json cache deserialize failed, key={}", key, ex);
            return null;
        }
    }

    public void set(String key, Object value, Duration ttl) {
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            return;
        }
        try {
            stringRedisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl);
        } catch (RuntimeException ex) {
            log.warn("Redis json cache unavailable, key={}", key, ex);
        } catch (Exception ex) {
            log.warn("Redis json cache serialize failed, key={}", key, ex);
        }
    }

    public void delete(String key) {
        try {
            stringRedisTemplate.delete(key);
        } catch (RuntimeException ex) {
            log.warn("Redis json cache delete failed, key={}", key, ex);
        }
    }
}
