package com.lexiflow.infra.redis;

import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class RedisCacheInvalidationSubscriber implements MessageListener {

    private final List<RedisCacheInvalidationListener> listeners;

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
