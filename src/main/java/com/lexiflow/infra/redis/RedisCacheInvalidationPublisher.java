package com.lexiflow.infra.redis;

import com.lexiflow.ai.prompt.domain.AiPromptFeatureType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class RedisCacheInvalidationPublisher {

    private final StringRedisTemplate stringRedisTemplate;

    public void publishPromptEvict(AiPromptFeatureType featureType) {
        if (featureType != null) {
            publish(RedisKeys.promptEvictPayload(featureType));
        }
    }

    public void publishPublicAiConfigEvict() {
        publish(RedisKeys.PUBLIC_AI_CONFIG_EVICT_PAYLOAD);
    }

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
