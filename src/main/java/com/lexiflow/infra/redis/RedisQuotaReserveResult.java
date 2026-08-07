package com.lexiflow.infra.redis;

/**
 * Redis 配额预留结果。
 * <p>
 * 表示配额预留的四种状态：
 * <ul>
 *   <li>允许（allowed = true）</li>
 *   <li>配额已耗尽（exhausted = true）</li>
 *   <li>Redis 不可用（unavailable = true）</li>
 *   <li>需要初始化（initializationRequired = true）</li>
 * </ul>
 * </p>
 *
 * @param allowed                是否允许
 * @param exhausted              配额是否已耗尽
 * @param unavailable            Redis 是否不可用
 * @param initializationRequired 是否需要初始化
 * @param used                   当前已使用量
 */
public record RedisQuotaReserveResult(boolean allowed, boolean exhausted, boolean unavailable, boolean initializationRequired, long used) {

    /** 创建允许使用的结果。 */
    public static RedisQuotaReserveResult allowed(long used) {
        return new RedisQuotaReserveResult(true, false, false, false, used);
    }

    /** 创建配额已耗尽的结果。 */
    public static RedisQuotaReserveResult exhaustedResult() {
        return new RedisQuotaReserveResult(false, true, false, false, -1);
    }

    /** 创建 Redis 不可用的结果。 */
    public static RedisQuotaReserveResult unavailableResult() {
        return new RedisQuotaReserveResult(false, false, true, false, -1);
    }

    /** 创建需要初始化的结果。 */
    public static RedisQuotaReserveResult initializationRequiredResult() {
        return new RedisQuotaReserveResult(false, false, false, true, -1);
    }
}
