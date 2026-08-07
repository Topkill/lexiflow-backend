package com.lexiflow.ai.core.service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.lexiflow.ai.content.domain.AiContentType;
import com.lexiflow.ai.content.domain.WordAiQa;
import com.lexiflow.ai.content.mapper.WordAiQaMapper;
import com.lexiflow.infra.redis.RedisAiHitCountBuffer;
import com.lexiflow.quiz.cloze.domain.ClozeQuiz;
import com.lexiflow.quiz.cloze.mapper.ClozeQuizMapper;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * AI 缓存命中计数刷写服务
 * <p>
 * 定时将 Redis 中缓冲的 AI 缓存命中计数批量刷写到数据库。
 * 支持单词问答和填空题两种内容类型的命中计数更新。
 * 刷写失败时会将计数重新入队，确保数据不丢失。
 * </p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiHitCountFlushService {

    private final RedisAiHitCountBuffer redisAiHitCountBuffer;
    private final WordAiQaMapper wordAiQaMapper;
    private final ClozeQuizMapper clozeQuizMapper;

    /**
     * 定时刷写缓冲的命中计数到数据库
     * <p>
     * 从 Redis 缓冲区中取出所有类型的命中计数，分别刷写到对应的数据库表中。
     * </p>
     */
    @Scheduled(
            initialDelayString = "${lexiflow.redis.ai-hit.flush-initial-delay-ms:30000}",
            fixedDelayString = "${lexiflow.redis.ai-hit.flush-delay-ms:30000}"
    )
    public void flushBufferedHits() {
        flushContentType(AiContentType.WORD_QA);
        flushContentType(AiContentType.CLOZE);
    }

    private void flushContentType(AiContentType contentType) {
        Map<Long, Long> hits = redisAiHitCountBuffer.drainHits(contentType);
        for (Map.Entry<Long, Long> entry : hits.entrySet()) {
            Long resultId = entry.getKey();
            long delta = entry.getValue();
            try {
                if (contentType == AiContentType.WORD_QA) {
                    flushWordQaHit(resultId, delta);
                } else if (contentType == AiContentType.CLOZE) {
                    flushClozeHit(resultId, delta);
                }
            } catch (RuntimeException ex) {
                redisAiHitCountBuffer.requeueHits(contentType, resultId, delta);
                log.warn("AI hit count flush failed, contentType={}, resultId={}, delta={}", contentType, resultId, delta, ex);
            }
        }
    }

    private void flushWordQaHit(Long resultId, long delta) {
        wordAiQaMapper.update(null, new UpdateWrapper<WordAiQa>()
                .setSql("hit_count = hit_count + " + delta)
                .eq("id", resultId));
    }

    private void flushClozeHit(Long resultId, long delta) {
        clozeQuizMapper.update(null, new UpdateWrapper<ClozeQuiz>()
                .setSql("hit_count = hit_count + " + delta)
                .eq("id", resultId));
    }
}
