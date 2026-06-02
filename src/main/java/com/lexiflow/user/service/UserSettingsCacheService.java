package com.lexiflow.user.service;

import com.lexiflow.infra.redis.RedisJsonCacheService;
import com.lexiflow.infra.redis.RedisKeys;
import com.lexiflow.user.domain.AiKeyMode;
import com.lexiflow.user.domain.TargetExam;
import com.lexiflow.user.domain.UserSettings;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class UserSettingsCacheService {

    static final Duration CACHE_TTL = Duration.ofHours(1);

    private final RedisJsonCacheService redisJsonCacheService;

    public UserSettings get(Long userId) {
        if (userId == null) {
            return null;
        }
        UserSettingsSnapshot snapshot = redisJsonCacheService.get(RedisKeys.userSettingsKey(userId), UserSettingsSnapshot.class);
        if (snapshot == null) {
            return null;
        }
        if (!userId.equals(snapshot.userId()) || !snapshot.isUsable()) {
            redisJsonCacheService.delete(RedisKeys.userSettingsKey(userId));
            return null;
        }
        return snapshot.toUserSettings();
    }

    public void put(UserSettings settings) {
        if (settings == null || settings.getUserId() == null) {
            return;
        }
        Long userId = settings.getUserId();
        redisJsonCacheService.delete(RedisKeys.userSettingsKey(userId));
        if (Integer.valueOf(1).equals(settings.getDeleted())) {
            return;
        }
        redisJsonCacheService.set(RedisKeys.userSettingsKey(userId), UserSettingsSnapshot.from(settings), CACHE_TTL);
    }

    public void evict(Long userId) {
        if (userId == null) {
            return;
        }
        redisJsonCacheService.delete(RedisKeys.userSettingsKey(userId));
    }

    public record UserSettingsSnapshot(
            Long id,
            Long userId,
            TargetExam targetExam,
            Integer dailyNewWords,
            AiKeyMode aiKeyMode,
            Boolean enableDailyReport,
            String timezone,
            Integer deleted
    ) {

        static UserSettingsSnapshot from(UserSettings settings) {
            return new UserSettingsSnapshot(
                    settings.getId(),
                    settings.getUserId(),
                    settings.getTargetExam(),
                    settings.getDailyNewWords(),
                    settings.getAiKeyMode(),
                    settings.getEnableDailyReport(),
                    settings.getTimezone(),
                    settings.getDeleted()
            );
        }

        boolean isUsable() {
            return id != null
                    && userId != null
                    && dailyNewWords != null
                    && aiKeyMode != null
                    && enableDailyReport != null
                    && StringUtils.hasText(timezone)
                    && !Integer.valueOf(1).equals(deleted);
        }

        UserSettings toUserSettings() {
            UserSettings settings = new UserSettings();
            settings.setId(id);
            settings.setUserId(userId);
            settings.setTargetExam(targetExam);
            settings.setDailyNewWords(dailyNewWords);
            settings.setAiKeyMode(aiKeyMode);
            settings.setEnableDailyReport(enableDailyReport);
            settings.setTimezone(timezone);
            settings.setDeleted(deleted);
            return settings;
        }
    }
}
