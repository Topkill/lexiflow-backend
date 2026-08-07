package com.lexiflow.infra.redis;

import com.lexiflow.ai.prompt.domain.AiPromptFeatureType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Redis 缓存失效事件发布器。
 * <p>
 * 通过 Redis Pub/Sub 发布缓存失效事件，通知其他节点清除本地缓存。
 * </p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RedisCacheInvalidationPublisher {

    private final StringRedisTemplate stringRedisTemplate;

    /** 发布 AI 提示词模板缓存失效事件。 */
    public void publishPromptEvict(AiPromptFeatureType featureType) {
        if (featureType != null) {
            publish(RedisKeys.promptEvictPayload(featureType));
        }
    }

    /** 发布公共 AI 配置缓存失效事件。 */
    public void publishPublicAiConfigEvict() {
        publish(RedisKeys.PUBLIC_AI_CONFIG_EVICT_PAYLOAD);
    }

    /** 发布缓存失效事件到 Redis Pub/Sub 通道。 */
    public void publish(String payload) {
        if (payload == null || payload.isBlank()) {
            return;
        }
        try {
            stringRedisTemplate.convertAndSend(RedisKeys.CACHE_EVICT_CHANNEL, payload);
        } catch (RuntimeException ex) {
            log.warn("Redis cache invalidation publish failed, payload={}", payload, ex);
        }
    }
}
