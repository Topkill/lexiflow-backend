package com.lexiflow.ai.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lexiflow.ai.config.domain.AiPublicConfig;
import com.lexiflow.ai.config.mapper.AiPublicConfigMapper;
import com.lexiflow.ai.config.mapper.UserAiConfigMapper;
import com.lexiflow.ai.core.dto.AiRuntimeConfig;
import com.lexiflow.infra.crypto.ApiKeyCryptoService;
import com.lexiflow.infra.redis.RedisJsonCacheService;
import com.lexiflow.infra.redis.RedisKeys;
import com.lexiflow.user.domain.AiKeyMode;
import com.lexiflow.user.domain.UserSettings;
import com.lexiflow.user.service.UserService;
import java.math.BigDecimal;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AiConfigResolverTest {

    @Mock
    private UserService userService;
    @Mock
    private UserAiConfigMapper userAiConfigMapper;
    @Mock
    private AiPublicConfigMapper aiPublicConfigMapper;
    @Mock
    private ApiKeyCryptoService apiKeyCryptoService;
    @Mock
    private RedisJsonCacheService redisJsonCacheService;

    @Test
    void publicConfigShouldUseShortLocalCacheAndRedisInvalidationShouldClearIt() {
        AiConfigResolver resolver = new AiConfigResolver(userService, userAiConfigMapper, aiPublicConfigMapper, apiKeyCryptoService, redisJsonCacheService);
        UserSettings settings = new UserSettings();
        settings.setAiKeyMode(AiKeyMode.PUBLIC);
        AiPublicConfig config = publicConfig();
        when(userService.getOrCreateSettings(9L)).thenReturn(settings);
        when(redisJsonCacheService.get(RedisKeys.aiPublicConfigKey(), AiConfigResolver.PublicConfigSnapshot.class))
                .thenReturn(null)
                .thenReturn(null);
        when(aiPublicConfigMapper.selectOne(any())).thenReturn(config);
        when(apiKeyCryptoService.decrypt("encrypted-key")).thenReturn("plain-key");

        AiRuntimeConfig first = resolver.resolve(9L);
        AiRuntimeConfig second = resolver.resolve(9L);
        resolver.onCacheInvalidation(RedisKeys.PUBLIC_AI_CONFIG_EVICT_PAYLOAD);
        AiRuntimeConfig third = resolver.resolve(9L);

        assertThat(first.apiKey()).isEqualTo("plain-key");
        assertThat(second).isSameAs(first);
        assertThat(third.apiKey()).isEqualTo("plain-key");
        verify(aiPublicConfigMapper, times(2)).selectOne(any());
        verify(redisJsonCacheService).delete(RedisKeys.aiPublicConfigKey());
    }

    @Test
    void publicConfigShouldUseRedisSnapshotBeforeDatabase() {
        AiConfigResolver resolver = new AiConfigResolver(userService, userAiConfigMapper, aiPublicConfigMapper, apiKeyCryptoService, redisJsonCacheService);
        UserSettings settings = new UserSettings();
        settings.setAiKeyMode(AiKeyMode.PUBLIC);
        when(userService.getOrCreateSettings(9L)).thenReturn(settings);
        when(redisJsonCacheService.get(RedisKeys.aiPublicConfigKey(), AiConfigResolver.PublicConfigSnapshot.class))
                .thenReturn(publicConfigSnapshot());
        when(apiKeyCryptoService.decrypt("encrypted-key")).thenReturn("plain-key");

        AiRuntimeConfig config = resolver.resolve(9L);

        assertThat(config.apiKey()).isEqualTo("plain-key");
        assertThat(config.modelName()).isEqualTo("test-model");
        verifyNoInteractions(aiPublicConfigMapper);
    }

    @Test
    void publicConfigDatabaseFallbackShouldWriteEncryptedSnapshotToRedis() {
        AiConfigResolver resolver = new AiConfigResolver(userService, userAiConfigMapper, aiPublicConfigMapper, apiKeyCryptoService, redisJsonCacheService);
        UserSettings settings = new UserSettings();
        settings.setAiKeyMode(AiKeyMode.PUBLIC);
        when(userService.getOrCreateSettings(9L)).thenReturn(settings);
        when(redisJsonCacheService.get(RedisKeys.aiPublicConfigKey(), AiConfigResolver.PublicConfigSnapshot.class))
                .thenReturn(null);
        when(aiPublicConfigMapper.selectOne(any())).thenReturn(publicConfig());
        when(apiKeyCryptoService.decrypt("encrypted-key")).thenReturn("plain-key");
        ArgumentCaptor<AiConfigResolver.PublicConfigSnapshot> snapshotCaptor =
                ArgumentCaptor.forClass(AiConfigResolver.PublicConfigSnapshot.class);
        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);

        AiRuntimeConfig config = resolver.resolve(9L);

        assertThat(config.apiKey()).isEqualTo("plain-key");
        verify(redisJsonCacheService).set(
                eq(RedisKeys.aiPublicConfigKey()),
                snapshotCaptor.capture(),
                ttlCaptor.capture()
        );
        assertThat(snapshotCaptor.getValue().encryptedApiKey()).isEqualTo("encrypted-key");
        assertThat(snapshotCaptor.getValue().encryptedApiKey()).isNotEqualTo("plain-key");
        assertThat(ttlCaptor.getValue()).isGreaterThan(Duration.ZERO);
    }

    @Test
    void publicConfigShouldFallbackToDatabaseWhenRedisSnapshotCannotBeDecrypted() {
        AiConfigResolver resolver = new AiConfigResolver(userService, userAiConfigMapper, aiPublicConfigMapper, apiKeyCryptoService, redisJsonCacheService);
        UserSettings settings = new UserSettings();
        settings.setAiKeyMode(AiKeyMode.PUBLIC);
        when(userService.getOrCreateSettings(9L)).thenReturn(settings);
        when(redisJsonCacheService.get(RedisKeys.aiPublicConfigKey(), AiConfigResolver.PublicConfigSnapshot.class))
                .thenReturn(publicConfigSnapshot());
        when(apiKeyCryptoService.decrypt("encrypted-key"))
                .thenThrow(new IllegalStateException("bad redis snapshot"))
                .thenReturn("plain-key");
        when(aiPublicConfigMapper.selectOne(any())).thenReturn(publicConfig());

        AiRuntimeConfig config = resolver.resolve(9L);

        assertThat(config.apiKey()).isEqualTo("plain-key");
        verify(redisJsonCacheService).delete(RedisKeys.aiPublicConfigKey());
        verify(aiPublicConfigMapper).selectOne(any());
    }

    private AiPublicConfig publicConfig() {
        AiPublicConfig config = new AiPublicConfig();
        config.setApiBaseUrl("http://localhost:8115/v1");
        config.setEncryptedApiKey("encrypted-key");
        config.setModelName("test-model");
        config.setTemperature(new BigDecimal("0.2"));
        config.setStreamEnabled(true);
        config.setDailyQuotaPerUser(10);
        return config;
    }

    private AiConfigResolver.PublicConfigSnapshot publicConfigSnapshot() {
        return new AiConfigResolver.PublicConfigSnapshot(
                "http://localhost:8115/v1",
                "encrypted-key",
                "test-model",
                new BigDecimal("0.2"),
                true,
                10
        );
    }
}
