package com.lexiflow.study.statistics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lexiflow.infra.redis.RedisJsonCacheService;
import com.lexiflow.infra.redis.RedisKeys;
import com.lexiflow.quiz.cloze.dto.ClozeAccuracyAggregate;
import com.lexiflow.quiz.cloze.mapper.ClozeAttemptMapper;
import com.lexiflow.study.domain.StudyPlan;
import com.lexiflow.study.domain.StudyPlanStatus;
import com.lexiflow.study.mapper.StudyPlanMapper;
import com.lexiflow.study.progress.mapper.StudyEventMapper;
import com.lexiflow.study.progress.mapper.UserWordStateMapper;
import com.lexiflow.study.progress.mapper.WrongWordMapper;
import com.lexiflow.study.statistics.dto.StudyStatisticsOverviewResponse;
import com.lexiflow.study.task.domain.DailyTask;
import com.lexiflow.study.task.domain.DailyTaskType;
import com.lexiflow.study.task.mapper.DailyTaskMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
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

    @Test
    void overviewShouldUseSqlAggregatesOnCacheMiss() {
        StudyStatisticsService service = newService();
        StudyPlan plan = new StudyPlan();
        plan.setId(10L);
        plan.setWordbookId(20L);
        plan.setStatus(StudyPlanStatus.ACTIVE);
        plan.setTotalWords(100);
        plan.setLearnedCount(25);
        when(studyPlanMapper.selectOne(any())).thenReturn(plan);
        when(userWordStateMapper.selectCount(any())).thenReturn(12L, 3L, 2L, 4L);
        when(wrongWordMapper.selectCount(any())).thenReturn(5L);
        LocalDate today = LocalDate.now();
        when(studyEventMapper.selectActiveDates(9L)).thenReturn(List.of(
                today,
                today.minusDays(1),
                today.minusDays(2),
                today.minusDays(4)
        ));
        DailyTask task = new DailyTask();
        task.setTaskType(DailyTaskType.DAILY);
        task.setNewCount(2);
        task.setReviewCount(1);
        task.setExtraCount(1);
        task.setDoneCount(3);
        when(dailyTaskMapper.selectOne(any())).thenReturn(task);
        ClozeAccuracyAggregate aggregate = new ClozeAccuracyAggregate();
        aggregate.setTotalBlanks(10L);
        aggregate.setCorrectCount(7L);
        when(clozeAttemptMapper.sumAccuracy(9L, 20L)).thenReturn(aggregate);

        StudyStatisticsOverviewResponse response = service.overview(9L);

        assertThat(response.streakDays()).isEqualTo(3);
        assertThat(response.todayTaskCompletionRate()).isEqualByComparingTo(new BigDecimal("75.00"));
        assertThat(response.clozeAccuracy()).isEqualByComparingTo(new BigDecimal("70.00"));
        assertThat(response.currentWordbookProgress()).isEqualByComparingTo(new BigDecimal("25.00"));
        verify(studyEventMapper).selectActiveDates(9L);
        verify(studyEventMapper, never()).selectList(any());
        verify(clozeAttemptMapper).sumAccuracy(9L, 20L);
        verify(clozeAttemptMapper, never()).selectList(any());
    }

    @Test
    void overviewShouldReturnZeroClozeAccuracyWhenAggregateTotalIsZero() {
        StudyStatisticsService service = newService();
        when(studyPlanMapper.selectOne(any())).thenReturn(null);
        when(userWordStateMapper.selectCount(any())).thenReturn(0L, 0L, 0L, 0L);
        when(wrongWordMapper.selectCount(any())).thenReturn(0L);
        when(studyEventMapper.selectActiveDates(9L)).thenReturn(List.of());
        ClozeAccuracyAggregate aggregate = new ClozeAccuracyAggregate();
        aggregate.setTotalBlanks(0L);
        aggregate.setCorrectCount(0L);
        when(clozeAttemptMapper.sumAccuracy(9L, null)).thenReturn(aggregate);

        StudyStatisticsOverviewResponse response = service.overview(9L);

        assertThat(response.clozeAccuracy()).isEqualByComparingTo(BigDecimal.ZERO.setScale(2));
    }

    private StudyStatisticsService newService() {
        return new StudyStatisticsService(
                userWordStateMapper,
                wrongWordMapper,
                studyEventMapper,
                studyPlanMapper,
                dailyTaskMapper,
                clozeAttemptMapper,
                redisJsonCacheService
        );
    }
}
