package com.lexiflow.infra.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * Redis 缓存失效订阅配置类。
 * <p>
 * 配置 Redis Pub/Sub 消息监听容器，用于监听缓存失效事件。
 * 仅在 Redis 连接可用且配置启用时生效。
 * </p>
 */
@Configuration
@RequiredArgsConstructor
public class RedisCacheInvalidationConfig {

    private final RedisCacheInvalidationSubscriber subscriber;

    /** 创建 Redis 缓存失效消息监听容器。 */
    @Bean
    @ConditionalOnBean(RedisConnectionFactory.class)
    @ConditionalOnProperty(prefix = "lexiflow.redis.cache-invalidation", name = "enabled", havingValue = "true", matchIfMissing = true)
    public RedisMessageListenerContainer redisCacheInvalidationContainer(RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(subscriber, new ChannelTopic(RedisKeys.CACHE_EVICT_CHANNEL));
        return container;
    }
}
