package com.lexiflow.ai.core.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lexiflow.ai.config.domain.AiPublicConfig;
import com.lexiflow.ai.config.domain.UserAiConfig;
import com.lexiflow.ai.config.mapper.AiPublicConfigMapper;
import com.lexiflow.ai.config.mapper.UserAiConfigMapper;
import com.lexiflow.ai.core.dto.AiConfigScope;
import com.lexiflow.ai.core.dto.AiRuntimeConfig;
import com.lexiflow.common.error.ErrorCode;
import com.lexiflow.common.exception.BizException;
import com.lexiflow.infra.crypto.ApiKeyCryptoService;
import com.lexiflow.infra.redis.RedisCacheInvalidationListener;
import com.lexiflow.infra.redis.RedisJsonCacheService;
import com.lexiflow.infra.redis.RedisKeys;
import com.lexiflow.user.domain.AiKeyMode;
import com.lexiflow.user.domain.UserSettings;
import com.lexiflow.user.service.UserService;
import java.math.BigDecimal;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * AI 配置解析服务
 * <p>
 * 根据用户的 AI 密钥模式解析出运行时配置。
 * 支持公共配置（带本地缓存 + Redis 缓存 + 数据库回源）和私有配置（用户独立配置）。
 * 实现 {@link RedisCacheInvalidationListener} 以监听 Redis Pub/Sub 缓存失效事件，
 * 保证分布式环境下公共配置缓存的一致性。
 * </p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiConfigResolver implements RedisCacheInvalidationListener {

    private static final long PUBLIC_CONFIG_CACHE_TTL_MILLIS = Duration.ofSeconds(30).toMillis();
    private static final Duration PUBLIC_CONFIG_REDIS_TTL = Duration.ofMinutes(30);

    private final UserService userService;
    private final UserAiConfigMapper userAiConfigMapper;
    private final AiPublicConfigMapper aiPublicConfigMapper;
    private final ApiKeyCryptoService apiKeyCryptoService;
    private final RedisJsonCacheService redisJsonCacheService;
    private volatile CachedPublicConfig cachedPublicConfig;

    /**
     * 解析指定用户的 AI 运行时配置
     * <p>
     * 根据用户设置中的 AI 密钥模式，返回公共配置或私有配置。
     * </p>
     *
     * @param userId 用户 ID
     * @return AI 运行时配置
     * @throws BizException 当配置不可用时
     */
    public AiRuntimeConfig resolve(Long userId) {
        UserSettings settings = userService.getOrCreateSettings(userId);
        if (settings.getAiKeyMode() == AiKeyMode.PRIVATE) {
            return resolvePrivateConfig(userId);
        }
        return resolvePublicConfig();
    }

    private AiRuntimeConfig resolvePrivateConfig(Long userId) {
        UserAiConfig config = userAiConfigMapper.selectOne(new LambdaQueryWrapper<UserAiConfig>()
                .eq(UserAiConfig::getUserId, userId)
                .eq(UserAiConfig::getEnabled, true)
                .last("LIMIT 1"));
        if (config == null || !StringUtils.hasText(config.getEncryptedApiKey())) {
            throw new BizException(ErrorCode.AI_CONFIG_UNAVAILABLE, "用户私有 AI 配置不可用");
        }
        return new AiRuntimeConfig(
                AiConfigScope.PRIVATE,
                config.getApiBaseUrl(),
                apiKeyCryptoService.decrypt(config.getEncryptedApiKey()),
                config.getModelName(),
                config.getTemperature(),
                config.getStreamEnabled(),
                null
        );
    }

    private AiRuntimeConfig resolvePublicConfig() {
        long now = System.currentTimeMillis();
        CachedPublicConfig cached = cachedPublicConfig;
        if (cached != null && cached.expiresAtMillis() > now) {
            return cached.config();
        }
        AiRuntimeConfig config = loadPublicConfig();
        cachedPublicConfig = new CachedPublicConfig(config, now + PUBLIC_CONFIG_CACHE_TTL_MILLIS);
        return config;
    }

    private AiRuntimeConfig loadPublicConfig() {
        PublicConfigSnapshot cached = redisJsonCacheService.get(RedisKeys.aiPublicConfigKey(), PublicConfigSnapshot.class);
        AiRuntimeConfig cachedConfig = toRuntimeConfigOrNull(cached);
        if (cachedConfig != null) {
            return cachedConfig;
        }
        AiPublicConfig config = loadPublicConfigFromDatabase();
        redisJsonCacheService.set(RedisKeys.aiPublicConfigKey(), PublicConfigSnapshot.from(config), PUBLIC_CONFIG_REDIS_TTL);
        return toRuntimeConfig(config);
    }

    private AiRuntimeConfig toRuntimeConfigOrNull(PublicConfigSnapshot cached) {
        if (cached == null) {
            return null;
        }
        if (!cached.isUsable()) {
            redisJsonCacheService.delete(RedisKeys.aiPublicConfigKey());
            return null;
        }
        try {
            return cached.toRuntimeConfig(apiKeyCryptoService);
        } catch (RuntimeException ex) {
            redisJsonCacheService.delete(RedisKeys.aiPublicConfigKey());
            log.warn("Redis public AI config decrypt failed, fallback to database", ex);
            return null;
        }
    }

    private AiPublicConfig loadPublicConfigFromDatabase() {
        AiPublicConfig config = aiPublicConfigMapper.selectOne(new LambdaQueryWrapper<AiPublicConfig>()
                .eq(AiPublicConfig::getActive, true)
                .eq(AiPublicConfig::getEnabled, true)
                .last("LIMIT 1"));
        if (config == null || !StringUtils.hasText(config.getEncryptedApiKey())) {
            throw new BizException(ErrorCode.AI_CONFIG_UNAVAILABLE, "公共 AI 配置不可用");
        }
        return config;
    }

    private AiRuntimeConfig toRuntimeConfig(AiPublicConfig config) {
        return new AiRuntimeConfig(
                AiConfigScope.PUBLIC,
                config.getApiBaseUrl(),
                apiKeyCryptoService.decrypt(config.getEncryptedApiKey()),
                config.getModelName(),
                config.getTemperature(),
                config.getStreamEnabled(),
                config.getDailyQuotaPerUser()
        );
    }

    /**
     * 清除公共配置的本地缓存和 Redis 缓存
     */
    public void evictPublicConfigCache() {
        cachedPublicConfig = null;
        redisJsonCacheService.delete(RedisKeys.aiPublicConfigKey());
    }

    /**
     * 处理 Redis Pub/Sub 缓存失效通知
     *
     * @param payload 缓存失效载荷
     */
    @Override
    public void onCacheInvalidation(String payload) {
        if (RedisKeys.PUBLIC_AI_CONFIG_EVICT_PAYLOAD.equals(payload)) {
            evictPublicConfigCache();
        }
    }

    private record CachedPublicConfig(AiRuntimeConfig config, long expiresAtMillis) {
    }

    public record PublicConfigSnapshot(
            String apiBaseUrl,
            String encryptedApiKey,
            String modelName,
            BigDecimal temperature,
            Boolean streamEnabled,
            Integer dailyQuotaPerUser
    ) {

        static PublicConfigSnapshot from(AiPublicConfig config) {
            return new PublicConfigSnapshot(
                    config.getApiBaseUrl(),
                    config.getEncryptedApiKey(),
                    config.getModelName(),
                    config.getTemperature(),
                    config.getStreamEnabled(),
                    config.getDailyQuotaPerUser()
            );
        }

        boolean isUsable() {
            return StringUtils.hasText(apiBaseUrl)
                    && StringUtils.hasText(encryptedApiKey)
                    && StringUtils.hasText(modelName);
        }

        AiRuntimeConfig toRuntimeConfig(ApiKeyCryptoService apiKeyCryptoService) {
            return new AiRuntimeConfig(
                    AiConfigScope.PUBLIC,
                    apiBaseUrl,
                    apiKeyCryptoService.decrypt(encryptedApiKey),
                    modelName,
                    temperature,
                    streamEnabled,
                    dailyQuotaPerUser
            );
        }
    }
}
