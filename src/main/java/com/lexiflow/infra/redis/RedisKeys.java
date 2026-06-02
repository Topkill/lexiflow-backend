package com.lexiflow.infra.redis;

import com.lexiflow.ai.content.domain.AiContentType;
import com.lexiflow.ai.prompt.domain.AiPromptFeatureType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;

public final class RedisKeys {

    public static final String CACHE_EVICT_CHANNEL = "lexiflow:cache:evict";
    public static final String PUBLIC_AI_CONFIG_EVICT_PAYLOAD = "ai-public-config";
    private static final String PROMPT_EVICT_PREFIX = "prompt:";
    private static final DateTimeFormatter BASIC_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private RedisKeys() {
    }

    public static String aiQuotaKey(LocalDate date, Long userId) {
        return "lexiflow:ai:quota:" + BASIC_DATE.format(date) + ":" + userId;
    }

    public static String aiLockKey(AiContentType contentType, String cacheKey) {
        return "lexiflow:ai:lock:" + contentType.name().toLowerCase() + ":" + sha256(cacheKey).substring(0, 32);
    }

    public static String aiHitCountHashKey(AiContentType contentType) {
        return "lexiflow:ai:hit:" + contentType.name().toLowerCase();
    }

    public static String studyStatisticsOverviewKey(Long userId) {
        return "lexiflow:study:stats:overview:" + userId;
    }

    public static String adminOverviewKey() {
        return "lexiflow:admin:overview";
    }

    public static String authLoginFailureKey(String scope, String value) {
        return "lexiflow:auth:login:" + scope + ":" + sha256(value).substring(0, 32);
    }

    public static String authRevokedAccessTokenKey(String tokenId) {
        return "lexiflow:auth:revoked:access:" + sha256(tokenId).substring(0, 32);
    }

    public static String authRevokedRefreshTokenKey(String tokenId) {
        return "lexiflow:auth:revoked:refresh:" + sha256(tokenId).substring(0, 32);
    }

    public static String promptEvictPayload(AiPromptFeatureType featureType) {
        return PROMPT_EVICT_PREFIX + featureType.name();
    }

    public static AiPromptFeatureType parsePromptEvictPayload(String payload) {
        if (payload == null || !payload.startsWith(PROMPT_EVICT_PREFIX)) {
            return null;
        }
        try {
            return AiPromptFeatureType.valueOf(payload.substring(PROMPT_EVICT_PREFIX.length()));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to calculate Redis key hash", ex);
        }
    }
}
