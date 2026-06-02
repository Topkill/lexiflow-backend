package com.lexiflow.infra.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lexiflow.ai.content.domain.AiContentType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

@ExtendWith(MockitoExtension.class)
class RedisAiHitCountBufferTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @Test
    void incrementHitShouldUseRedisHash() {
        RedisAiHitCountBuffer buffer = new RedisAiHitCountBuffer(stringRedisTemplate);
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);

        boolean incremented = buffer.incrementHit(AiContentType.WORD_QA, 12L);

        assertThat(incremented).isTrue();
        verify(hashOperations).increment("lexiflow:ai:hit:word_qa", "12", 1);
    }

    @Test
    void drainHitsShouldDrainEachFieldWithLua() {
        RedisAiHitCountBuffer buffer = new RedisAiHitCountBuffer(stringRedisTemplate);
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.entries("lexiflow:ai:hit:cloze")).thenReturn(Map.of("34", "3"));
        when(stringRedisTemplate.execute(any(DefaultRedisScript.class), eq(List.of("lexiflow:ai:hit:cloze")), eq("34"), eq("3")))
                .thenReturn(3L);

        Map<Long, Long> drained = buffer.drainHits(AiContentType.CLOZE);

        assertThat(drained).containsEntry(34L, 3L);
    }
}
