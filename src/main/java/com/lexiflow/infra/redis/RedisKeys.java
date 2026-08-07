package com.lexiflow.infra.redis;

import com.lexiflow.ai.content.domain.AiContentType;
import com.lexiflow.ai.prompt.domain.AiPromptFeatureType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;

/**
 * Redis 键名常量与工具类。
 * <p>
 * 定义所有 Redis 缓存键的命名规范和生成方法，
 * 同时提供缓存失效通道和 Pub/Sub 相关常量。
 * </p>
 */
public final class RedisKeys {

    /** 缓存失效 Pub/Sub 通道名称 */
    public static final String CACHE_EVICT_CHANNEL = "lexiflow:cache:evict";
    /** 公共 AI 配置缓存失效负载 */
    public static final String PUBLIC_AI_CONFIG_EVICT_PAYLOAD = "ai-public-config";
    private static final String PROMPT_EVICT_PREFIX = "prompt:";
    private static final DateTimeFormatter BASIC_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private RedisKeys() {
    }

    /** 生成用户每日 AI 配额缓存键。 */
    public static String aiQuotaKey(LocalDate date, Long userId) {
        return "lexiflow:ai:quota:" + BASIC_DATE.format(date) + ":" + userId;
    }

    /** 生成 AI 内容生成锁缓存键。 */
    public static String aiLockKey(AiContentType contentType, String cacheKey) {
        return "lexiflow:ai:lock:" + contentType.name().toLowerCase() + ":" + sha256(cacheKey).substring(0, 32);
    }

    /** 生成完形填空任务创建锁缓存键。 */
    public static String aiClozeTaskCreateLockKey(Long userId, Long dailyTaskId, String sourceType, int targetWordCount) {
        String raw = userId + ":" + dailyTaskId + ":" + sourceType + ":" + targetWordCount;
        return "lexiflow:ai:cloze-task:create:" + sha256(raw).substring(0, 32);
    }

    /** 生成单词 QA 任务创建锁缓存键。 */
    public static String aiWordQaTaskCreateLockKey(Long userId, Long wordbookId, Long wordId, String sourceHash) {
        String raw = userId + ":" + wordbookId + ":" + wordId + ":" + sourceHash;
        return "lexiflow:ai:word-qa-task:create:" + sha256(raw).substring(0, 32);
    }

    /** 生成完形填空评审任务创建锁缓存键。 */
    public static String aiClozeReviewTaskCreateLockKey(Long userId, Long attemptId, String sourceHash) {
        String raw = userId + ":" + attemptId + ":" + sourceHash;
        return "lexiflow:ai:cloze-review-task:create:" + sha256(raw).substring(0, 32);
    }

    /** 生成 AI 调用计数 Hash 键。 */
    public static String aiHitCountHashKey(AiContentType contentType) {
        return "lexiflow:ai:hit:" + contentType.name().toLowerCase();
    }

    /** 获取公共 AI 配置缓存键。 */
    public static String aiPublicConfigKey() {
        return "lexiflow:ai:public-config";
    }

    /** 生成学习统计概览缓存键。 */
    public static String studyStatisticsOverviewKey(Long userId) {
        return "lexiflow:study:stats:overview:" + userId;
    }

    /** 获取管理后台概览缓存键。 */
    public static String adminOverviewKey() {
        return "lexiflow:admin:overview";
    }

    /** 生成登录失败计数缓存键。 */
    public static String authLoginFailureKey(String scope, String value) {
        return "lexiflow:auth:login:" + scope + ":" + sha256(value).substring(0, 32);
    }

    /** 生成登录验证码缓存键。 */
    public static String authLoginCaptchaKey(String captchaId) {
        return "lexiflow:auth:captcha:" + sha256(captchaId).substring(0, 32);
    }

    /** 生成验证码请求频率限制缓存键。 */
    public static String authLoginCaptchaRateKey(String clientIp) {
        return "lexiflow:auth:captcha:rate:" + sha256(clientIp).substring(0, 32);
    }

    /** 生成已撤销访问令牌缓存键。 */
    public static String authRevokedAccessTokenKey(String tokenId) {
        return "lexiflow:auth:revoked:access:" + sha256(tokenId).substring(0, 32);
    }

    /** 生成已撤销刷新令牌缓存键。 */
    public static String authRevokedRefreshTokenKey(String tokenId) {
        return "lexiflow:auth:revoked:refresh:" + sha256(tokenId).substring(0, 32);
    }

    /** 生成刷新令牌会话缓存键。 */
    public static String authRefreshSessionKey(String tokenId) {
        return "lexiflow:auth:refresh:session:" + sha256(tokenId).substring(0, 32);
    }

    /** 生成用户信息缓存键。 */
    public static String authUserKey(Long userId) {
        return "lexiflow:auth:user:" + userId;
    }

    /** 生成用户令牌版本缓存键。 */
    public static String authTokenVersionKey(Long userId) {
        return "lexiflow:auth:token:version:" + userId;
    }

    /** 生成用户设置缓存键。 */
    public static String userSettingsKey(Long userId) {
        return "lexiflow:user:settings:" + userId;
    }

    /** 生成提示词模板缓存失效负载。 */
    public static String promptEvictPayload(AiPromptFeatureType featureType) {
        return PROMPT_EVICT_PREFIX + featureType.name();
    }

    /** 解析提示词模板缓存失效负载。 */
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

    /** 对字符串进行 SHA-256 哈希。 */
    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to calculate Redis key hash", ex);
        }
    }
}
