package com.lexiflow.infra.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

@ExtendWith(MockitoExtension.class)
class RedisDistributedLockServiceTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    @Test
    void tryLockShouldReturnAcquiredWithOwnerTokenAndTtl() {
        RedisDistributedLockService service = new RedisDistributedLockService(stringRedisTemplate);
        Duration ttl = Duration.ofSeconds(130);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("lock:key"), anyString(), eq(ttl))).thenReturn(true);

        RedisLockAttempt attempt = service.tryLock("lock:key", ttl);

        assertThat(attempt.acquired()).isTrue();
        assertThat(attempt.lock().key()).isEqualTo("lock:key");
        assertThat(attempt.lock().ownerToken()).isNotBlank();
    }

    @Test
    void tryLockShouldReturnHeldWhenKeyAlreadyExists() {
        RedisDistributedLockService service = new RedisDistributedLockService(stringRedisTemplate);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("lock:key"), anyString(), any(Duration.class))).thenReturn(false);

        RedisLockAttempt attempt = service.tryLock("lock:key", Duration.ofSeconds(130));

        assertThat(attempt.acquired()).isFalse();
        assertThat(attempt.unavailable()).isFalse();
        assertThat(attempt.lock().key()).isEqualTo("lock:key");
    }

    @Test
    void releaseShouldUseOwnerTokenCompareAndDelete() {
        RedisDistributedLockService service = new RedisDistributedLockService(stringRedisTemplate);
        when(stringRedisTemplate.execute(any(DefaultRedisScript.class), eq(List.of("lock:key")), eq("owner-token")))
                .thenReturn(1L);

        boolean released = service.release(new RedisLock("lock:key", "owner-token"));

        assertThat(released).isTrue();
        ArgumentCaptor<DefaultRedisScript> scriptCaptor = ArgumentCaptor.forClass(DefaultRedisScript.class);
        verify(stringRedisTemplate).execute(scriptCaptor.capture(), eq(List.of("lock:key")), eq("owner-token"));
        assertThat(scriptCaptor.getValue().getScriptAsString()).contains("redis.call('get', KEYS[1]) == ARGV[1]");
    }

    @Test
    void releaseShouldReturnFalseWhenOwnerTokenDoesNotMatch() {
        RedisDistributedLockService service = new RedisDistributedLockService(stringRedisTemplate);
        when(stringRedisTemplate.execute(any(DefaultRedisScript.class), eq(List.of("lock:key")), eq("owner-token")))
                .thenReturn(0L);

        boolean released = service.release(new RedisLock("lock:key", "owner-token"));

        assertThat(released).isFalse();
    }
}
