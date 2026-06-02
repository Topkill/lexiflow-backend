package com.lexiflow.infra.redis;

public interface RedisCacheInvalidationListener {

    void onCacheInvalidation(String payload);
}
