package com.lexiflow.ai.core.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.lexiflow.ai.content.domain.AiContentType;
import com.lexiflow.ai.content.domain.WordAiQa;
import com.lexiflow.ai.content.mapper.WordAiQaMapper;
import com.lexiflow.infra.redis.RedisAiHitCountBuffer;
import com.lexiflow.quiz.cloze.domain.ClozeQuiz;
import com.lexiflow.quiz.cloze.mapper.ClozeQuizMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AiHitCountFlushServiceTest {

    @Mock
    private RedisAiHitCountBuffer redisAiHitCountBuffer;
    @Mock
    private WordAiQaMapper wordAiQaMapper;
    @Mock
    private ClozeQuizMapper clozeQuizMapper;

    @Test
    void flushBufferedHitsShouldUpdateWordQaAndClozeCounts() {
        AiHitCountFlushService service = new AiHitCountFlushService(redisAiHitCountBuffer, wordAiQaMapper, clozeQuizMapper);
        when(redisAiHitCountBuffer.drainHits(AiContentType.WORD_QA)).thenReturn(Map.of(12L, 2L));
        when(redisAiHitCountBuffer.drainHits(AiContentType.CLOZE)).thenReturn(Map.of(34L, 3L));

        service.flushBufferedHits();

        verify(wordAiQaMapper).update(isNull(), any(Wrapper.class));
        verify(clozeQuizMapper).update(isNull(), any(Wrapper.class));
    }

    @Test
    void flushBufferedHitsShouldRequeueWhenMysqlUpdateFails() {
        AiHitCountFlushService service = new AiHitCountFlushService(redisAiHitCountBuffer, wordAiQaMapper, clozeQuizMapper);
        when(redisAiHitCountBuffer.drainHits(AiContentType.WORD_QA)).thenReturn(Map.of(12L, 2L));
        doThrow(new RuntimeException("db down")).when(wordAiQaMapper).update(isNull(WordAiQa.class), any(Wrapper.class));

        service.flushBufferedHits();

        verify(redisAiHitCountBuffer).requeueHits(eq(AiContentType.WORD_QA), eq(12L), eq(2L));
    }
}
