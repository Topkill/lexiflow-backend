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

/**
 * 用户设置缓存服务。
 *
 * <p>基于 Redis 对用户设置进行缓存，TTL 为 1 小时。
 * 通过 {@link UserSettingsSnapshot} 中间快照实现实体与缓存数据的转换，
 * 并在读取时校验快照完整性，无效数据自动清除。</p>
 */
@Service
@RequiredArgsConstructor
public class UserSettingsCacheService {

    /** 缓存 TTL：1 小时 */
    static final Duration CACHE_TTL = Duration.ofHours(1);

    private final RedisJsonCacheService redisJsonCacheService;

    /**
     * 从缓存中获取用户设置。
     *
     * @param userId 用户 ID
     * @return 缓存的用户设置，缓存未命中或数据无效时返回 null
     */
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

    /**
     * 将用户设置写入缓存。
     *
     * <p>若设置已被逻辑删除，则仅清除缓存不写入新数据。</p>
     *
     * @param settings 用户设置实体
     */
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

    /** 清除指定用户的设置缓存 */
    public void evict(Long userId) {
        if (userId == null) {
            return;
        }
        redisJsonCacheService.delete(RedisKeys.userSettingsKey(userId));
    }

    /**
     * 用户设置的 Redis 缓存快照。
     *
     * <p>作为实体与 Redis JSON 之间的中间表示，
     * 包含完整性校验逻辑 {@link #isUsable()}，确保缓存数据可用。</p>
     */
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

        /** 从 UserSettings 实体创建快照 */
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

        /** 校验快照数据完整性，确保所有必要字段均不为空且未被删除 */
        boolean isUsable() {
            return id != null
                    && userId != null
                    && dailyNewWords != null
                    && aiKeyMode != null
                    && enableDailyReport != null
                    && StringUtils.hasText(timezone)
                    && !Integer.valueOf(1).equals(deleted);
        }

        /** 将快照还原为 UserSettings 实体 */
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
