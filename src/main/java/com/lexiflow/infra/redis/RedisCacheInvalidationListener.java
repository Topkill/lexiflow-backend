package com.lexiflow.infra.redis;

/**
 * Redis 缓存失效监听器接口。
 * <p>
 * 实现此接口以监听 Redis Pub/Sub 缓存失效事件，
 * 当缓存被其他节点失效时触发回调。
 * </p>
 */
public interface RedisCacheInvalidationListener {

    /**
     * 缓存失效回调方法。
     *
     * @param payload 失效事件负载（标识被失效的缓存类型）
     */
    void onCacheInvalidation(String payload);
}
