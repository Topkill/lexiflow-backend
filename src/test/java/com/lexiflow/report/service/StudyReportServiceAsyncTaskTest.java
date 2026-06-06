package com.lexiflow.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexiflow.ai.content.domain.AiContentType;
import com.lexiflow.ai.core.service.AiGatewayService;
import com.lexiflow.ai.core.dto.AiChatCompletionResult;
import com.lexiflow.ai.core.dto.AiPrompt;
import com.lexiflow.async.domain.AsyncTask;
import com.lexiflow.async.domain.AsyncTaskStatus;
import com.lexiflow.async.domain.AsyncTaskType;
import com.lexiflow.async.service.AsyncTaskService;
import com.lexiflow.common.error.ErrorCode;
import com.lexiflow.common.exception.BizException;
import com.lexiflow.quiz.cloze.dto.ClozeAccuracyAggregate;
import com.lexiflow.quiz.cloze.mapper.ClozeAttemptMapper;
import com.lexiflow.report.domain.StudyReport;
import com.lexiflow.report.dto.CreateReportTaskRequest;
import com.lexiflow.report.dto.CreateReportTaskResponse;
import com.lexiflow.report.mapper.StudyReportMapper;
import com.lexiflow.report.mq.StudyReportTaskPublisher;
import com.lexiflow.study.domain.StudyPlan;
import com.lexiflow.study.mapper.StudyPlanMapper;
import com.lexiflow.study.progress.dto.StudyEventCorrectWrongCount;
import com.lexiflow.study.progress.domain.WrongWord;
import com.lexiflow.study.progress.mapper.StudyEventMapper;
import com.lexiflow.study.progress.mapper.WrongWordMapper;
import com.lexiflow.study.task.domain.DailyTask;
import com.lexiflow.study.task.mapper.DailyTaskMapper;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;

@ExtendWith(MockitoExtension.class)
class StudyReportServiceAsyncTaskTest {

    private static final Long USER_ID = 10L;
    private static final Long DAILY_TASK_ID = 20L;
    private static final Long ASYNC_TASK_ID = 30L;
    private static final Long REPORT_ID = 40L;
    private static final LocalDate REPORT_DATE = LocalDate.of(2026, 6, 2);

    @Mock
    private AsyncTaskService asyncTaskService;
    @Mock
    private DailyTaskMapper dailyTaskMapper;
    @Mock
    private StudyReportTaskPublisher studyReportTaskPublisher;

    private ObjectMapper objectMapper;
    private TestableStudyReportService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new TestableStudyReportService(
                asyncTaskService,
                mock(AiGatewayService.class),
                mock(StudyReportMapper.class),
                dailyTaskMapper,
                mock(StudyPlanMapper.class),
                mock(StudyEventMapper.class),
                mock(WrongWordMapper.class),
                mock(ClozeAttemptMapper.class),
                objectMapper,
                studyReportTaskPublisher
        );
    }

    @Test
    void createReportTaskShouldCreatePendingTaskAndPublishMessage() throws Exception {
        mockOwnedDailyTask();
        AsyncTask task = asyncTask(AsyncTaskStatus.PENDING);
        when(asyncTaskService.createTask(eq(USER_ID), eq(AsyncTaskType.AI_REPORT), any())).thenReturn(task);
        ArgumentCaptor<String> requestJsonCaptor = ArgumentCaptor.forClass(String.class);

        CreateReportTaskResponse response = service.createReportTask(
                USER_ID,
                new CreateReportTaskRequest(DAILY_TASK_ID, REPORT_DATE)
        );

        verify(asyncTaskService).createTask(eq(USER_ID), eq(AsyncTaskType.AI_REPORT), requestJsonCaptor.capture());
        JsonNode requestJson = objectMapper.readTree(requestJsonCaptor.getValue());
        assertThat(response.taskId()).isEqualTo(String.valueOf(ASYNC_TASK_ID));
        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(requestJson.path("dailyTaskId").asText()).isEqualTo(String.valueOf(DAILY_TASK_ID));
        assertThat(requestJson.path("reportDate").asText()).isEqualTo(REPORT_DATE.toString());
        verify(studyReportTaskPublisher).publish(ASYNC_TASK_ID);
    }

    @Test
    void createReportTaskShouldMarkFailedWhenPublishFails() {
        mockOwnedDailyTask();
        AsyncTask task = asyncTask(AsyncTaskStatus.PENDING);
        when(asyncTaskService.createTask(eq(USER_ID), eq(AsyncTaskType.AI_REPORT), any())).thenReturn(task);
        doThrow(new AmqpException("rabbit down")).when(studyReportTaskPublisher).publish(ASYNC_TASK_ID);

        assertThatThrownBy(() -> service.createReportTask(
                USER_ID,
                new CreateReportTaskRequest(DAILY_TASK_ID, REPORT_DATE)
        ))
                .isInstanceOf(BizException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ASYNC_TASK_FAILED);

        verify(asyncTaskService).markPendingFailed(eq(ASYNC_TASK_ID), eq(String.valueOf(ErrorCode.ASYNC_TASK_FAILED.getCode())), any());
    }

    @Test
    void processReportTaskShouldGenerateAndMarkSuccessWhenTaskIsPending() {
        AsyncTask task = asyncTask(AsyncTaskStatus.PENDING);
        task.setRequestJson("""
                {"dailyTaskId":"20","reportDate":"2026-06-02"}
                """);
        when(asyncTaskService.getTaskEntity(ASYNC_TASK_ID)).thenReturn(task);
        when(asyncTaskService.markRunningIfPending(ASYNC_TASK_ID, "正在生成学习报告", 20)).thenReturn(true);
        mockOwnedDailyTask();

        service.processReportTask(ASYNC_TASK_ID, false);

        assertThat(service.generatedTaskId).isEqualTo(ASYNC_TASK_ID);
        verify(asyncTaskService).markSuccess(ASYNC_TASK_ID, REPORT_ID, "学习报告生成完成");
    }

    @Test
    void processReportTaskShouldSkipRunningTaskWhenMessageIsNotRedelivered() {
        AsyncTask task = asyncTask(AsyncTaskStatus.RUNNING);
        when(asyncTaskService.getTaskEntity(ASYNC_TASK_ID)).thenReturn(task);

        service.processReportTask(ASYNC_TASK_ID, false);

        verifyNoInteractions(dailyTaskMapper);
    }

    @Test
    void generateReportShouldUseSqlAggregateStats() {
        AiGatewayService aiGatewayService = mock(AiGatewayService.class);
        StudyReportMapper studyReportMapper = mock(StudyReportMapper.class);
        StudyPlanMapper studyPlanMapper = mock(StudyPlanMapper.class);
        StudyEventMapper studyEventMapper = mock(StudyEventMapper.class);
        WrongWordMapper wrongWordMapper = mock(WrongWordMapper.class);
        ClozeAttemptMapper clozeAttemptMapper = mock(ClozeAttemptMapper.class);
        StudyReportService reportService = new StudyReportService(
                asyncTaskService,
                aiGatewayService,
                studyReportMapper,
                dailyTaskMapper,
                studyPlanMapper,
                studyEventMapper,
                wrongWordMapper,
                clozeAttemptMapper,
                objectMapper,
                studyReportTaskPublisher
        );
        DailyTask dailyTask = new DailyTask();
        dailyTask.setId(DAILY_TASK_ID);
        dailyTask.setPlanId(100L);
        dailyTask.setNewCount(3);
        dailyTask.setReviewCount(2);
        StudyPlan plan = new StudyPlan();
        plan.setId(100L);
        plan.setWordbookId(200L);
        when(studyPlanMapper.selectById(100L)).thenReturn(plan);
        StudyEventCorrectWrongCount eventStats = new StudyEventCorrectWrongCount();
        eventStats.setCorrectCount(6L);
        eventStats.setWrongCount(2L);
        when(studyEventMapper.countCorrectWrongEvents(eq(USER_ID), eq(200L), any(), any())).thenReturn(eventStats);
        ClozeAccuracyAggregate quizStats = new ClozeAccuracyAggregate();
        quizStats.setTotalBlanks(10L);
        quizStats.setCorrectCount(8L);
        when(clozeAttemptMapper.sumAccuracyBetween(eq(USER_ID), eq(200L), any(), any())).thenReturn(quizStats);
        WrongWord wrongWord = new WrongWord();
        wrongWord.setWordId(300L);
        when(wrongWordMapper.selectList(any())).thenReturn(java.util.List.of(wrongWord));
        when(aiGatewayService.generateJson(eq(USER_ID), eq(AiContentType.REPORT), any(AiPrompt.class), eq(ASYNC_TASK_ID)))
                .thenReturn(new AiChatCompletionResult("""
                        {"summary":{"performance":"好","weakness":"少","tomorrowAdvice":"继续"},"markdownContent":"日报"}
                        """, 0, 0, 0));
        when(studyReportMapper.selectOne(any())).thenReturn(null);
        when(studyReportMapper.insert(any(StudyReport.class))).thenAnswer(invocation -> {
            StudyReport report = invocation.getArgument(0);
            report.setId(REPORT_ID);
            return 1;
        });

        StudyReport report = reportService.generateReport(USER_ID, dailyTask, ASYNC_TASK_ID, REPORT_DATE);

        assertThat(report.getId()).isEqualTo(REPORT_ID);
        assertThat(report.getQuizAccuracy()).isEqualByComparingTo("80.00");
        ArgumentCaptor<AiPrompt> promptCaptor = ArgumentCaptor.forClass(AiPrompt.class);
        verify(aiGatewayService).generateJson(eq(USER_ID), eq(AiContentType.REPORT), promptCaptor.capture(), eq(ASYNC_TASK_ID));
        assertThat(promptCaptor.getValue().userPrompt())
                .contains("\"correctEvents\":6")
                .contains("\"wrongEvents\":2")
                .contains("\"quizAccuracy\":80.00")
                .contains("\"wrongWordIds\":[\"300\"]");
        verify(studyEventMapper).countCorrectWrongEvents(eq(USER_ID), eq(200L), any(), any());
        verify(studyEventMapper, never()).selectCount(any());
        verify(clozeAttemptMapper).sumAccuracyBetween(eq(USER_ID), eq(200L), any(), any());
        verify(clozeAttemptMapper, never()).selectList(any());
    }

    private void mockOwnedDailyTask() {
        DailyTask task = new DailyTask();
        task.setId(DAILY_TASK_ID);
        task.setUserId(USER_ID);
        task.setPlanId(100L);
        when(dailyTaskMapper.selectOne(any())).thenReturn(task);
    }

    private AsyncTask asyncTask(AsyncTaskStatus status) {
        AsyncTask task = new AsyncTask();
        task.setId(ASYNC_TASK_ID);
        task.setUserId(USER_ID);
        task.setTaskType(AsyncTaskType.AI_REPORT);
        task.setStatus(status);
        return task;
    }

    private static class TestableStudyReportService extends StudyReportService {

        private Long generatedTaskId;

        TestableStudyReportService(
                AsyncTaskService asyncTaskService,
                AiGatewayService aiGatewayService,
                StudyReportMapper studyReportMapper,
                DailyTaskMapper dailyTaskMapper,
                StudyPlanMapper studyPlanMapper,
                StudyEventMapper studyEventMapper,
                WrongWordMapper wrongWordMapper,
                ClozeAttemptMapper clozeAttemptMapper,
                ObjectMapper objectMapper,
                StudyReportTaskPublisher studyReportTaskPublisher
        ) {
            super(
                    asyncTaskService,
                    aiGatewayService,
                    studyReportMapper,
                    dailyTaskMapper,
                    studyPlanMapper,
                    studyEventMapper,
                    wrongWordMapper,
                    clozeAttemptMapper,
                    objectMapper,
                    studyReportTaskPublisher
            );
        }

        @Override
        protected StudyReport generateReport(Long userId, DailyTask dailyTask, Long asyncTaskId, LocalDate reportDate) {
            generatedTaskId = asyncTaskId;
            StudyReport report = new StudyReport();
            report.setId(REPORT_ID);
            return report;
        }
    }
}
