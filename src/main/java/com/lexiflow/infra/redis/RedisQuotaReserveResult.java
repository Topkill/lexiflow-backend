package com.lexiflow.infra.redis;

public record RedisQuotaReserveResult(boolean allowed, boolean exhausted, boolean unavailable, boolean initializationRequired, long used) {

    public static RedisQuotaReserveResult allowed(long used) {
        return new RedisQuotaReserveResult(true, false, false, false, used);
    }

    public static RedisQuotaReserveResult exhaustedResult() {
        return new RedisQuotaReserveResult(false, true, false, false, -1);
    }

    public static RedisQuotaReserveResult unavailableResult() {
        return new RedisQuotaReserveResult(false, false, true, false, -1);
    }

    public static RedisQuotaReserveResult initializationRequiredResult() {
        return new RedisQuotaReserveResult(false, false, false, true, -1);
    }
}
