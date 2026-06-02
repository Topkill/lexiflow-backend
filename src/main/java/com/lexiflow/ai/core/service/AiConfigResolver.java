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
import com.lexiflow.infra.redis.RedisKeys;
import com.lexiflow.user.domain.AiKeyMode;
import com.lexiflow.user.domain.UserSettings;
import com.lexiflow.user.service.UserService;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AiConfigResolver implements RedisCacheInvalidationListener {

    private static final long PUBLIC_CONFIG_CACHE_TTL_MILLIS = Duration.ofSeconds(30).toMillis();

    private final UserService userService;
    private final UserAiConfigMapper userAiConfigMapper;
    private final AiPublicConfigMapper aiPublicConfigMapper;
    private final ApiKeyCryptoService apiKeyCryptoService;
    private volatile CachedPublicConfig cachedPublicConfig;

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
        AiPublicConfig config = aiPublicConfigMapper.selectOne(new LambdaQueryWrapper<AiPublicConfig>()
                .eq(AiPublicConfig::getActive, true)
                .eq(AiPublicConfig::getEnabled, true)
                .last("LIMIT 1"));
        if (config == null || !StringUtils.hasText(config.getEncryptedApiKey())) {
            throw new BizException(ErrorCode.AI_CONFIG_UNAVAILABLE, "公共 AI 配置不可用");
        }
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

    public void evictPublicConfigCache() {
        cachedPublicConfig = null;
    }

    @Override
    public void onCacheInvalidation(String payload) {
        if (RedisKeys.PUBLIC_AI_CONFIG_EVICT_PAYLOAD.equals(payload)) {
            evictPublicConfigCache();
        }
    }

    private record CachedPublicConfig(AiRuntimeConfig config, long expiresAtMillis) {
    }
}
