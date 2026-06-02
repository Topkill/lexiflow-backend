package com.lexiflow.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lexiflow.infra.redis.RedisJsonCacheService;
import com.lexiflow.infra.redis.RedisKeys;
import com.lexiflow.user.domain.AiKeyMode;
import com.lexiflow.user.domain.TargetExam;
import com.lexiflow.user.domain.UserSettings;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserSettingsCacheServiceTest {

    @Mock
    private RedisJsonCacheService redisJsonCacheService;

    @Test
    void getShouldReturnCachedUserSettings() {
        UserSettingsCacheService service = new UserSettingsCacheService(redisJsonCacheService);
        when(redisJsonCacheService.get(RedisKeys.userSettingsKey(7L), UserSettingsCacheService.UserSettingsSnapshot.class))
                .thenReturn(snapshot(7L, 0));

        UserSettings settings = service.get(7L);

        assertThat(settings).isNotNull();
        assertThat(settings.getId()).isEqualTo(11L);
        assertThat(settings.getUserId()).isEqualTo(7L);
        assertThat(settings.getTargetExam()).isEqualTo(TargetExam.CET4);
        assertThat(settings.getDailyNewWords()).isEqualTo(30);
        assertThat(settings.getAiKeyMode()).isEqualTo(AiKeyMode.PUBLIC);
        assertThat(settings.getEnableDailyReport()).isTrue();
        assertThat(settings.getTimezone()).isEqualTo("Asia/Shanghai");
    }

    @Test
    void getShouldIgnoreMismatchedOrDeletedSnapshot() {
        UserSettingsCacheService service = new UserSettingsCacheService(redisJsonCacheService);
        when(redisJsonCacheService.get(RedisKeys.userSettingsKey(7L), UserSettingsCacheService.UserSettingsSnapshot.class))
                .thenReturn(snapshot(8L, 0), snapshot(7L, 1));

        assertThat(service.get(7L)).isNull();
        assertThat(service.get(7L)).isNull();
    }

    @Test
    void putShouldWriteSnapshotWithTtl() {
        UserSettingsCacheService service = new UserSettingsCacheService(redisJsonCacheService);
        ArgumentCaptor<UserSettingsCacheService.UserSettingsSnapshot> snapshotCaptor =
                ArgumentCaptor.forClass(UserSettingsCacheService.UserSettingsSnapshot.class);
        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);

        service.put(settings());

        verify(redisJsonCacheService).delete(RedisKeys.userSettingsKey(7L));
        verify(redisJsonCacheService).set(
                eq(RedisKeys.userSettingsKey(7L)),
                snapshotCaptor.capture(),
                ttlCaptor.capture()
        );
        assertThat(snapshotCaptor.getValue().userId()).isEqualTo(7L);
        assertThat(snapshotCaptor.getValue().aiKeyMode()).isEqualTo(AiKeyMode.PUBLIC);
        assertThat(ttlCaptor.getValue()).isEqualTo(UserSettingsCacheService.CACHE_TTL);
    }

    @Test
    void putShouldOnlyDeleteDeletedSettings() {
        UserSettingsCacheService service = new UserSettingsCacheService(redisJsonCacheService);
        UserSettings settings = settings();
        settings.setDeleted(1);

        service.put(settings);

        verify(redisJsonCacheService).delete(RedisKeys.userSettingsKey(7L));
    }

    @Test
    void evictShouldDeleteUserSettingsKey() {
        UserSettingsCacheService service = new UserSettingsCacheService(redisJsonCacheService);

        service.evict(7L);

        verify(redisJsonCacheService).delete(RedisKeys.userSettingsKey(7L));
    }

    private UserSettingsCacheService.UserSettingsSnapshot snapshot(Long userId, Integer deleted) {
        return new UserSettingsCacheService.UserSettingsSnapshot(
                11L,
                userId,
                TargetExam.CET4,
                30,
                AiKeyMode.PUBLIC,
                true,
                "Asia/Shanghai",
                deleted
        );
    }

    private UserSettings settings() {
        UserSettings settings = new UserSettings();
        settings.setId(11L);
        settings.setUserId(7L);
        settings.setTargetExam(TargetExam.CET4);
        settings.setDailyNewWords(30);
        settings.setAiKeyMode(AiKeyMode.PUBLIC);
        settings.setEnableDailyReport(true);
        settings.setTimezone("Asia/Shanghai");
        settings.setDeleted(0);
        return settings;
    }
}
