package com.lexiflow.infra.redis;

public record RedisLock(String key, String ownerToken) {
}
