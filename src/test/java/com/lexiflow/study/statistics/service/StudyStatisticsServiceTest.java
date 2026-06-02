package com.lexiflow.study.statistics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lexiflow.infra.redis.RedisJsonCacheService;
import com.lexiflow.infra.redis.RedisKeys;
import com.lexiflow.quiz.cloze.mapper.ClozeAttemptMapper;
import com.lexiflow.study.mapper.StudyPlanMapper;
import com.lexiflow.study.progress.mapper.StudyEventMapper;
import com.lexiflow.study.progress.mapper.UserWordStateMapper;
import com.lexiflow.study.progress.mapper.WrongWordMapper;
import com.lexiflow.study.statistics.dto.StudyStatisticsOverviewResponse;
import com.lexiflow.study.task.mapper.DailyTaskMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StudyStatisticsServiceTest {

    @Mock
    private UserWordStateMapper userWordStateMapper;
    @Mock
    private WrongWordMapper wrongWordMapper;
    @Mock
    private StudyEventMapper studyEventMapper;
    @Mock
    private StudyPlanMapper studyPlanMapper;
    @Mock
    private DailyTaskMapper dailyTaskMapper;
    @Mock
    private ClozeAttemptMapper clozeAttemptMapper;
    @Mock
    private RedisJsonCacheService redisJsonCacheService;

    @Test
    void overviewShouldReturnRedisCacheHitWithoutQueryingDatabase() {
        StudyStatisticsService service = new StudyStatisticsService(
                userWordStateMapper,
                wrongWordMapper,
                studyEventMapper,
                studyPlanMapper,
                dailyTaskMapper,
                clozeAttemptMapper,
                redisJsonCacheService
        );
        StudyStatisticsOverviewResponse cached = new StudyStatisticsOverviewResponse(
                1L,
                1L,
                0L,
                0L,
                3,
                BigDecimal.ZERO.setScale(2),
                BigDecimal.ZERO.setScale(2),
                BigDecimal.ZERO.setScale(2),
                "10",
                "20"
        );
        when(redisJsonCacheService.get(RedisKeys.studyStatisticsOverviewKey(9L), StudyStatisticsOverviewResponse.class))
                .thenReturn(cached);

        StudyStatisticsOverviewResponse response = service.overview(9L);

        assertThat(response).isSameAs(cached);
        verifyNoInteractions(userWordStateMapper, wrongWordMapper, studyEventMapper, studyPlanMapper, dailyTaskMapper, clozeAttemptMapper);
    }
}
