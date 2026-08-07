package com.lexiflow.infra.redis;

import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

/**
 * Redis 缓存失效事件订阅者。
 * <p>
 * 监听 Redis Pub/Sub 缓存失效通道，将事件分发给所有注册的 {@link RedisCacheInvalidationListener}。
 * </p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RedisCacheInvalidationSubscriber implements MessageListener {

    private final List<RedisCacheInvalidationListener> listeners;

    /** 接收 Redis 消息并分发给所有监听器。 */
    @Override
    public void onMessage(Message message, byte[] pattern) {
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);
        for (RedisCacheInvalidationListener listener : listeners) {
            try {
                listener.onCacheInvalidation(payload);
            } catch (RuntimeException ex) {
                log.warn("Redis cache invalidation listener failed, payload={}", payload, ex);
            }
        }
    }
}
