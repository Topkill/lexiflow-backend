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

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthRateLimitService {

    private static final int MAX_FAILURES = 5;
    private static final int CAPTCHA_REQUIRED_FAILURES = 3;
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

    public void assertLoginAllowed(String email, String clientIp) {
        if (isBlocked(emailKey(email)) || isBlocked(ipKey(clientIp))) {
            throw new BizException(ErrorCode.TOO_MANY_REQUESTS, "登录失败次数过多，请稍后再试");
        }
    }

    public boolean requiresCaptcha(String email, String clientIp) {
        return reachesThreshold(emailKey(email), CAPTCHA_REQUIRED_FAILURES)
                || reachesThreshold(ipKey(clientIp), CAPTCHA_REQUIRED_FAILURES);
    }

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

    public record LoginFailureStatus(boolean captchaRequired, boolean blocked) {
    }
}
