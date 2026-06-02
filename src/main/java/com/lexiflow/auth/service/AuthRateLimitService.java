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

    public void recordLoginFailure(String email, String clientIp) {
        increment(emailKey(email));
        increment(ipKey(clientIp));
    }

    public void clearLoginFailures(String email) {
        try {
            stringRedisTemplate.delete(emailKey(email));
        } catch (RuntimeException ex) {
            log.warn("Redis login failure clear failed, email={}", normalizeEmail(email), ex);
        }
    }

    private boolean isBlocked(String key) {
        try {
            String value = stringRedisTemplate.opsForValue().get(key);
            if (!StringUtils.hasText(value)) {
                return false;
            }
            return Long.parseLong(value) >= MAX_FAILURES;
        } catch (NumberFormatException ex) {
            return false;
        } catch (RuntimeException ex) {
            log.warn("Redis login rate limit check failed, key={}", key, ex);
            return false;
        }
    }

    private void increment(String key) {
        try {
            stringRedisTemplate.execute(
                    INCREMENT_FAILURE_SCRIPT,
                    List.of(key),
                    String.valueOf(FAILURE_WINDOW.toMillis())
            );
        } catch (RuntimeException ex) {
            log.warn("Redis login rate limit increment failed, key={}", key, ex);
        }
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
}
