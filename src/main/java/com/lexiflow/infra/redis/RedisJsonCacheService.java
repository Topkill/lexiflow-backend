package com.lexiflow.infra.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Redis JSON 缓存服务。
 * <p>
 * 提供基于 JSON 序列化的 Redis 缓存读写操作，
 * 使用 Jackson 进行对象与 JSON 字符串的转换。
 * </p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RedisJsonCacheService {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 从 Redis 获取缓存并反序列化为指定类型。
     *
     * @param key       缓存键
     * @param valueType 目标类型
     * @return 反序列化后的对象，不存在或异常时返回 null
     */
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

    /**
     * 将对象序列化后存入 Redis 缓存。
     *
     * @param key   缓存键
     * @param value 要缓存的对象
     * @param ttl   缓存过期时间
     */
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

    /** 删除指定键的缓存。 */
    public void delete(String key) {
        try {
            stringRedisTemplate.delete(key);
        } catch (RuntimeException ex) {
            log.warn("Redis json cache delete failed, key={}", key, ex);
        }
    }
}
