package com.lexiflow.auth.service;

import com.lexiflow.common.error.ErrorCode;
import com.lexiflow.common.exception.BizException;
import com.lexiflow.infra.redis.RedisKeys;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 认证限流服务。
 *
 * <p>基于 Redis 实现登录失败计数和限流控制。
 * 按邮箱和 IP 两个维度统计失败次数：
 * 达到 3 次时要求输入验证码，达到 5 次时封禁登录。
 * 失败记录在 10 分钟窗口内有效，使用 Lua 脚本保证原子性。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthRateLimitService {

    /** 最大失败次数，达到后封禁登录 */
    private static final int MAX_FAILURES = 5;
    /** 要求验证码的失败次数阈值 */
    private static final int CAPTCHA_REQUIRED_FAILURES = 3;
    /** 失败记录的时间窗口 */
    private static final Duration FAILURE_WINDOW = Duration.ofMinutes(10);
    private static final DefaultRedisScript<Long> INCREMENT_FAILURE_SCRIPT = new DefaultRedisScript<>(
            """
                    local count = redis.call('INCR', KEYS[1])
                    if count == 1 then
                        redis.call('PEXPIRE', KEYS[1], ARGV[1])
                    end
                    return count
                    """,
            Long.class
    );

    private final StringRedisTemplate stringRedisTemplate;

    /** 检查是否允许登录（未被封禁） */
    public void assertLoginAllowed(String email, String clientIp) {
        if (isBlocked(emailKey(email)) || isBlocked(ipKey(clientIp))) {
            throw new BizException(ErrorCode.TOO_MANY_REQUESTS, "登录失败次数过多，请稍后再试");
        }
    }

    /** 检查是否需要验证码（失败次数达到阈值） */
    public boolean requiresCaptcha(String email, String clientIp) {
        return reachesThreshold(emailKey(email), CAPTCHA_REQUIRED_FAILURES)
                || reachesThreshold(ipKey(clientIp), CAPTCHA_REQUIRED_FAILURES);
    }

    /**
     * 记录登录失败并返回当前状态。
     *
     * @param email    用户邮箱
     * @param clientIp 客户端 IP
     * @return 登录失败状态，包含是否需要验证码和是否被封禁
     */
    public LoginFailureStatus recordLoginFailure(String email, String clientIp) {
        Long emailFailures = increment(emailKey(email));
        Long ipFailures = increment(ipKey(clientIp));
        return new LoginFailureStatus(
                reachesThreshold(emailFailures, CAPTCHA_REQUIRED_FAILURES)
                        || reachesThreshold(ipFailures, CAPTCHA_REQUIRED_FAILURES),
                reachesThreshold(emailFailures, MAX_FAILURES)
                        || reachesThreshold(ipFailures, MAX_FAILURES)
        );
    }

    /** 清除指定邮箱的登录失败记录 */
    public void clearLoginFailures(String email) {
        try {
            stringRedisTemplate.delete(emailKey(email));
        } catch (RuntimeException ex) {
            log.warn("Redis login failure clear failed, email={}", normalizeEmail(email), ex);
        }
    }

    private boolean isBlocked(String key) {
        return reachesThreshold(key, MAX_FAILURES);
    }

    private boolean reachesThreshold(String key, int threshold) {
        try {
            String value = stringRedisTemplate.opsForValue().get(key);
            if (!StringUtils.hasText(value)) {
                return false;
            }
            return Long.parseLong(value) >= threshold;
        } catch (NumberFormatException ex) {
            return false;
        } catch (RuntimeException ex) {
            log.warn("Redis login rate limit check failed, key={}", key, ex);
            return false;
        }
    }

    private Long increment(String key) {
        try {
            return stringRedisTemplate.execute(
                    INCREMENT_FAILURE_SCRIPT,
                    List.of(key),
                    String.valueOf(FAILURE_WINDOW.toMillis())
            );
        } catch (RuntimeException ex) {
            log.warn("Redis login rate limit increment failed, key={}", key, ex);
            return null;
        }
    }

    private boolean reachesThreshold(Long value, int threshold) {
        return value != null && value >= threshold;
    }

    private String emailKey(String email) {
        return RedisKeys.authLoginFailureKey("email", normalizeEmail(email));
    }

    private String ipKey(String clientIp) {
        return RedisKeys.authLoginFailureKey("ip", StringUtils.hasText(clientIp) ? clientIp.trim() : "unknown");
    }

    private String normalizeEmail(String email) {
        return StringUtils.hasText(email) ? email.trim().toLowerCase(Locale.ROOT) : "unknown";
    }

    /**
     * 登录失败状态记录。
     *
     * @param captchaRequired 是否需要验证码
     * @param blocked         是否被封禁
     */
    public record LoginFailureStatus(boolean captchaRequired, boolean blocked) {
    }
}
