package com.lexiflow.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lexiflow.admin.dto.AdminOverviewResponse;
import com.lexiflow.ai.content.mapper.AiCallLogMapper;
import com.lexiflow.infra.redis.RedisJsonCacheService;
import com.lexiflow.infra.redis.RedisKeys;
import com.lexiflow.study.progress.mapper.StudyEventMapper;
import com.lexiflow.user.mapper.UserMapper;
import com.lexiflow.wordbook.mapper.WordMapper;
import com.lexiflow.wordbook.mapper.WordbookMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminDashboardServiceTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private WordbookMapper wordbookMapper;
    @Mock
    private WordMapper wordMapper;
    @Mock
    private StudyEventMapper studyEventMapper;
    @Mock
    private AiCallLogMapper aiCallLogMapper;
    @Mock
    private RedisJsonCacheService redisJsonCacheService;

    @Test
    void overviewShouldReturnRedisCacheHitWithoutQueryingDatabase() {
        AdminDashboardService service = new AdminDashboardService(
                userMapper,
                wordbookMapper,
                wordMapper,
                studyEventMapper,
                aiCallLogMapper,
                redisJsonCacheService
        );
        AdminOverviewResponse cached = new AdminOverviewResponse(1L, 1L, 1L, 2L, 3L, 4L, new BigDecimal("100.00"), 1L);
        when(redisJsonCacheService.get(RedisKeys.adminOverviewKey(), AdminOverviewResponse.class)).thenReturn(cached);

        AdminOverviewResponse response = service.overview();

        assertThat(response).isSameAs(cached);
        verifyNoInteractions(userMapper, wordbookMapper, wordMapper, studyEventMapper, aiCallLogMapper);
    }

    @Test
    void overviewShouldUseSqlAggregateForTodayLearnersOnCacheMiss() {
        AdminDashboardService service = new AdminDashboardService(
                userMapper,
                wordbookMapper,
                wordMapper,
                studyEventMapper,
                aiCallLogMapper,
                redisJsonCacheService
        );
        when(userMapper.selectCount(any())).thenReturn(10L, 8L);
        when(studyEventMapper.countDistinctUsersBetween(any(), any())).thenReturn(3L);
        when(wordbookMapper.selectCount(any())).thenReturn(2L);
        when(wordMapper.selectCount(any())).thenReturn(500L);
        when(aiCallLogMapper.selectCount(any())).thenReturn(20L, 15L, 5L);

        AdminOverviewResponse response = service.overview();

        assertThat(response.todayLearners()).isEqualTo(3L);
        assertThat(response.aiSuccessRate()).isEqualByComparingTo(new BigDecimal("75.00"));
        verify(studyEventMapper).countDistinctUsersBetween(any(), any());
        verify(studyEventMapper, never()).selectList(any());
    }
}
