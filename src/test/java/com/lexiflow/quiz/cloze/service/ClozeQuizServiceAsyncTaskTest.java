package com.lexiflow.quiz.cloze.service;

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
import com.lexiflow.ai.core.service.AiGatewayService;
import com.lexiflow.ai.prompt.service.AiPromptOutputSchemaService;
import com.lexiflow.ai.prompt.service.AiPromptTemplateService;
import com.lexiflow.async.domain.AsyncTask;
import com.lexiflow.async.domain.AsyncTaskStatus;
import com.lexiflow.async.domain.AsyncTaskType;
import com.lexiflow.async.service.AsyncTaskService;
import com.lexiflow.common.error.ErrorCode;
import com.lexiflow.common.exception.BizException;
import com.lexiflow.infra.redis.RedisAiHitCountBuffer;
import com.lexiflow.infra.redis.RedisDistributedLockService;
import com.lexiflow.infra.redis.RedisKeys;
import com.lexiflow.infra.redis.RedisLockAttempt;
import com.lexiflow.quiz.cloze.domain.ClozeQuiz;
import com.lexiflow.quiz.cloze.domain.ClozeSourceType;
import com.lexiflow.quiz.cloze.dto.CreateClozeTaskRequest;
import com.lexiflow.quiz.cloze.dto.CreateClozeTaskResponse;
import com.lexiflow.quiz.cloze.mapper.ClozeAttemptAnswerMapper;
import com.lexiflow.quiz.cloze.mapper.ClozeAttemptMapper;
import com.lexiflow.quiz.cloze.mapper.ClozeQuizBlankMapper;
import com.lexiflow.quiz.cloze.mapper.ClozeQuizMapper;
import com.lexiflow.quiz.cloze.mq.ClozeGenerationTaskPublisher;
import com.lexiflow.study.progress.mapper.StudyEventMapper;
import com.lexiflow.study.progress.mapper.WrongWordMapper;
import com.lexiflow.study.progress.service.SpacedRepetitionService;
import com.lexiflow.study.task.domain.DailyTask;
import com.lexiflow.study.task.domain.DailyTaskItem;
import com.lexiflow.study.task.domain.DailyTaskStatus;
import com.lexiflow.study.task.domain.DailyTaskType;
import com.lexiflow.study.task.mapper.DailyTaskItemMapper;
import com.lexiflow.study.task.mapper.DailyTaskMapper;
import com.lexiflow.wordbook.mapper.WordMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class ClozeQuizServiceAsyncTaskTest {

    private static final Long USER_ID = 10L;
    private static final Long DAILY_TASK_ID = 20L;
    private static final Long WORDBOOK_ID = 30L;
    private static final Long ASYNC_TASK_ID = 40L;

    @Mock
    private AsyncTaskService asyncTaskService;
    @Mock
    private DailyTaskMapper dailyTaskMapper;
    @Mock
    private DailyTaskItemMapper dailyTaskItemMapper;
    @Mock
    private ClozeGenerationTaskPublisher clozeGenerationTaskPublisher;
    @Mock
    private RedisDistributedLockService redisDistributedLockService;

    private ObjectMapper objectMapper;
    private TestableClozeQuizService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new TestableClozeQuizService(
                asyncTaskService,
                mock(AiGatewayService.class),
                mock(ClozeBlankWordSelector.class),
                dailyTaskMapper,
                dailyTaskItemMapper,
                mock(WordMapper.class),
                mock(ClozeQuizMapper.class),
                mock(ClozeQuizBlankMapper.class),
                mock(ClozeAttemptMapper.class),
                mock(ClozeAttemptAnswerMapper.class),
                mock(StudyEventMapper.class),
                mock(WrongWordMapper.class),
                objectMapper,
                mock(SpacedRepetitionService.class),
                mock(AiPromptTemplateService.class),
                mock(AiPromptOutputSchemaService.class),
                mock(TransactionTemplate.class),
                mock(RedisAiHitCountBuffer.class),
                redisDistributedLockService,
                clozeGenerationTaskPublisher
        );
    }

    @Test
    void createClozeTaskShouldCreatePendingTaskAndPublishMessage() throws Exception {
        mockOwnedDailyTaskWithWordbook();
        when(asyncTaskService.listRecentTasks(USER_ID, AsyncTaskType.AI_CLOZE, 50)).thenReturn(java.util.List.of());
        AsyncTask task = asyncTask(AsyncTaskStatus.PENDING);
        when(asyncTaskService.createTask(eq(USER_ID), eq(AsyncTaskType.AI_CLOZE), any())).thenReturn(task);
        ArgumentCaptor<String> requestJsonCaptor = ArgumentCaptor.forClass(String.class);

        CreateClozeTaskResponse response = service.createClozeTask(
                USER_ID,
                new CreateClozeTaskRequest(DAILY_TASK_ID, ClozeSourceType.COMPLETED_GROUP, 10, false)
        );

        verify(asyncTaskService).createTask(eq(USER_ID), eq(AsyncTaskType.AI_CLOZE), requestJsonCaptor.capture());
        JsonNode requestJson = objectMapper.readTree(requestJsonCaptor.getValue());
        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(requestJson.path("dailyTaskId").asText()).isEqualTo(String.valueOf(DAILY_TASK_ID));
        assertThat(requestJson.path("wordbookId").asText()).isEqualTo(String.valueOf(WORDBOOK_ID));
        assertThat(requestJson.path("sourceType").asText()).isEqualTo("COMPLETED_GROUP");
        assertThat(requestJson.path("trigger").asText()).isEqualTo("USER");
        verify(clozeGenerationTaskPublisher).publish(ASYNC_TASK_ID);
    }

    @Test
    void createClozeTaskShouldReusePendingTaskWithSamePayload() {
        mockOwnedDailyTaskWithWordbook();
        AsyncTask reusableTask = asyncTask(AsyncTaskStatus.RUNNING);
        reusableTask.setRequestJson("""
                {"dailyTaskId":"20","wordbookId":"30","sourceType":"COMPLETED_GROUP","targetWordCount":10,"regenerate":false,"trigger":"PREFETCH"}
                """);
        when(asyncTaskService.listRecentTasks(USER_ID, AsyncTaskType.AI_CLOZE, 50)).thenReturn(java.util.List.of(reusableTask));

        CreateClozeTaskResponse response = service.createClozeTask(
                USER_ID,
                new CreateClozeTaskRequest(DAILY_TASK_ID, ClozeSourceType.COMPLETED_GROUP, 10, false)
        );

        assertThat(response.taskId()).isEqualTo(String.valueOf(ASYNC_TASK_ID));
        assertThat(response.status()).isEqualTo("RUNNING");
        verifyNoInteractions(clozeGenerationTaskPublisher);
    }

    @Test
    void createClozeTaskShouldWaitAndReuseTaskWhenCreateLockIsHeld() {
        mockOwnedDailyTaskWithWordbook();
        String lockKey = RedisKeys.aiClozeTaskCreateLockKey(USER_ID, DAILY_TASK_ID, ClozeSourceType.COMPLETED_GROUP.name(), 10);
        AsyncTask reusableTask = asyncTask(AsyncTaskStatus.PENDING);
        reusableTask.setRequestJson("""
                {"dailyTaskId":"20","wordbookId":"30","sourceType":"COMPLETED_GROUP","targetWordCount":10,"regenerate":false,"trigger":"USER"}
                """);
        when(redisDistributedLockService.tryLock(eq(lockKey), any())).thenReturn(RedisLockAttempt.held(lockKey));
        when(asyncTaskService.listRecentTasks(USER_ID, AsyncTaskType.AI_CLOZE, 50)).thenReturn(java.util.List.of(reusableTask));

        CreateClozeTaskResponse response = service.createClozeTask(
                USER_ID,
                new CreateClozeTaskRequest(DAILY_TASK_ID, ClozeSourceType.COMPLETED_GROUP, 10, true)
        );

        assertThat(response.taskId()).isEqualTo(String.valueOf(ASYNC_TASK_ID));
        assertThat(response.status()).isEqualTo("PENDING");
        verify(asyncTaskService, never()).createTask(any(), any(), any());
        verifyNoInteractions(clozeGenerationTaskPublisher);
    }

    @Test
    void createClozeTaskShouldReuseRunningTaskEvenWhenRegenerateIsRequested() {
        mockOwnedDailyTaskWithWordbook();
        AsyncTask reusableTask = asyncTask(AsyncTaskStatus.RUNNING);
        reusableTask.setRequestJson("""
                {"dailyTaskId":"20","wordbookId":"30","sourceType":"COMPLETED_GROUP","targetWordCount":10,"regenerate":false,"trigger":"USER"}
                """);
        when(asyncTaskService.listRecentTasks(USER_ID, AsyncTaskType.AI_CLOZE, 50)).thenReturn(java.util.List.of(reusableTask));

        CreateClozeTaskResponse response = service.createClozeTask(
                USER_ID,
                new CreateClozeTaskRequest(DAILY_TASK_ID, ClozeSourceType.COMPLETED_GROUP, 10, true)
        );

        assertThat(response.taskId()).isEqualTo(String.valueOf(ASYNC_TASK_ID));
        assertThat(response.status()).isEqualTo("RUNNING");
        verifyNoInteractions(clozeGenerationTaskPublisher);
    }

    @Test
    void createClozeTaskShouldCreateNewTaskWhenRegenerateIsRequestedAndOnlySuccessExists() {
        mockOwnedDailyTaskWithWordbook();
        AsyncTask successTask = asyncTask(AsyncTaskStatus.SUCCESS);
        successTask.setResultId(990L);
        successTask.setRequestJson("""
                {"dailyTaskId":"20","wordbookId":"30","sourceType":"COMPLETED_GROUP","targetWordCount":10,"regenerate":false,"trigger":"USER"}
                """);
        AsyncTask newTask = asyncTask(41L, AsyncTaskStatus.PENDING);
        when(asyncTaskService.listRecentTasks(USER_ID, AsyncTaskType.AI_CLOZE, 50)).thenReturn(java.util.List.of(successTask));
        when(asyncTaskService.createTask(eq(USER_ID), eq(AsyncTaskType.AI_CLOZE), any())).thenReturn(newTask);

        CreateClozeTaskResponse response = service.createClozeTask(
                USER_ID,
                new CreateClozeTaskRequest(DAILY_TASK_ID, ClozeSourceType.COMPLETED_GROUP, 10, true)
        );

        assertThat(response.taskId()).isEqualTo("41");
        assertThat(response.status()).isEqualTo("PENDING");
        verify(clozeGenerationTaskPublisher).publish(41L);
    }

    @Test
    void createClozeTaskShouldMarkFailedWhenPublishFails() {
        mockOwnedDailyTaskWithWordbook();
        when(asyncTaskService.listRecentTasks(USER_ID, AsyncTaskType.AI_CLOZE, 50)).thenReturn(java.util.List.of());
        AsyncTask task = asyncTask(AsyncTaskStatus.PENDING);
        when(asyncTaskService.createTask(eq(USER_ID), eq(AsyncTaskType.AI_CLOZE), any())).thenReturn(task);
        doThrow(new AmqpException("rabbit down")).when(clozeGenerationTaskPublisher).publish(ASYNC_TASK_ID);

        assertThatThrownBy(() -> service.createClozeTask(
                USER_ID,
                new CreateClozeTaskRequest(DAILY_TASK_ID, ClozeSourceType.COMPLETED_GROUP, 10, false)
        ))
                .isInstanceOf(BizException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ASYNC_TASK_FAILED);

        verify(asyncTaskService).markFailed(eq(ASYNC_TASK_ID), eq(String.valueOf(ErrorCode.ASYNC_TASK_FAILED.getCode())), any());
    }

    @Test
    void prefetchCompletedGroupClozeShouldCreateTaskAfterCommit() {
        mockOwnedDailyTaskWithWordbook();
        when(asyncTaskService.listRecentTasks(USER_ID, AsyncTaskType.AI_CLOZE, 50)).thenReturn(java.util.List.of());
        AsyncTask task = asyncTask(AsyncTaskStatus.PENDING);
        when(asyncTaskService.createTask(eq(USER_ID), eq(AsyncTaskType.AI_CLOZE), any())).thenReturn(task);

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.prefetchCompletedGroupCloze(USER_ID, DAILY_TASK_ID);

            verifyNoInteractions(asyncTaskService, clozeGenerationTaskPublisher);
            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        verify(asyncTaskService).createTask(eq(USER_ID), eq(AsyncTaskType.AI_CLOZE), any());
        verify(clozeGenerationTaskPublisher).publish(ASYNC_TASK_ID);
    }

    @Test
    void processClozeTaskShouldGenerateAndMarkSuccessWhenTaskIsPending() {
        AsyncTask task = asyncTask(AsyncTaskStatus.PENDING);
        task.setRequestJson("""
                {"dailyTaskId":"20","wordbookId":"30","sourceType":"COMPLETED_GROUP","targetWordCount":10,"regenerate":false,"trigger":"USER"}
                """);
        when(asyncTaskService.getTaskEntity(ASYNC_TASK_ID)).thenReturn(task);
        when(asyncTaskService.markRunningIfPending(ASYNC_TASK_ID, "正在生成完形填空", 20)).thenReturn(true);
        mockOwnedDailyTask();

        service.processClozeTask(ASYNC_TASK_ID, false);

        assertThat(service.generatedTaskId).isEqualTo(ASYNC_TASK_ID);
        verify(asyncTaskService).markSuccess(ASYNC_TASK_ID, 990L, "完形填空生成完成");
    }

    @Test
    void processClozeTaskShouldSkipRunningTaskWhenMessageIsNotRedelivered() {
        AsyncTask task = asyncTask(AsyncTaskStatus.RUNNING);
        when(asyncTaskService.getTaskEntity(ASYNC_TASK_ID)).thenReturn(task);

        service.processClozeTask(ASYNC_TASK_ID, false);

        verifyNoInteractions(dailyTaskMapper);
    }

    private void mockOwnedDailyTaskWithWordbook() {
        mockOwnedDailyTask();
        DailyTaskItem item = new DailyTaskItem();
        item.setDailyTaskId(DAILY_TASK_ID);
        item.setWordbookId(WORDBOOK_ID);
        when(dailyTaskItemMapper.selectOne(any())).thenReturn(item);
    }

    private void mockOwnedDailyTask() {
        DailyTask task = new DailyTask();
        task.setId(DAILY_TASK_ID);
        task.setUserId(USER_ID);
        task.setStatus(DailyTaskStatus.DONE);
        task.setTaskType(DailyTaskType.DAILY);
        when(dailyTaskMapper.selectOne(any())).thenReturn(task);
    }

    private AsyncTask asyncTask(AsyncTaskStatus status) {
        return asyncTask(ASYNC_TASK_ID, status);
    }

    private AsyncTask asyncTask(Long taskId, AsyncTaskStatus status) {
        AsyncTask task = new AsyncTask();
        task.setId(taskId);
        task.setUserId(USER_ID);
        task.setTaskType(AsyncTaskType.AI_CLOZE);
        task.setStatus(status);
        return task;
    }

    private static class TestableClozeQuizService extends ClozeQuizService {

        private Long generatedTaskId;

        TestableClozeQuizService(
                AsyncTaskService asyncTaskService,
                AiGatewayService aiGatewayService,
                ClozeBlankWordSelector clozeBlankWordSelector,
                DailyTaskMapper dailyTaskMapper,
                DailyTaskItemMapper dailyTaskItemMapper,
                WordMapper wordMapper,
                ClozeQuizMapper clozeQuizMapper,
                ClozeQuizBlankMapper clozeQuizBlankMapper,
                ClozeAttemptMapper clozeAttemptMapper,
                ClozeAttemptAnswerMapper clozeAttemptAnswerMapper,
                StudyEventMapper studyEventMapper,
                WrongWordMapper wrongWordMapper,
                ObjectMapper objectMapper,
                SpacedRepetitionService spacedRepetitionService,
                AiPromptTemplateService aiPromptTemplateService,
                AiPromptOutputSchemaService outputSchemaService,
                TransactionTemplate transactionTemplate,
                RedisAiHitCountBuffer redisAiHitCountBuffer,
                RedisDistributedLockService redisDistributedLockService,
                ClozeGenerationTaskPublisher clozeGenerationTaskPublisher
        ) {
            super(
                    asyncTaskService,
                    aiGatewayService,
                    clozeBlankWordSelector,
                    dailyTaskMapper,
                    dailyTaskItemMapper,
                    wordMapper,
                    clozeQuizMapper,
                    clozeQuizBlankMapper,
                    clozeAttemptMapper,
                    clozeAttemptAnswerMapper,
                    studyEventMapper,
                    wrongWordMapper,
                    objectMapper,
                    spacedRepetitionService,
                    aiPromptTemplateService,
                    outputSchemaService,
                    transactionTemplate,
                    redisAiHitCountBuffer,
                    redisDistributedLockService,
                    clozeGenerationTaskPublisher
            );
        }

        @Override
        protected ClozeQuiz generateQuiz(Long userId, DailyTask dailyTask, Long wordbookId, Long asyncTaskId, ClozeSourceType sourceType, int targetWordCount, boolean regenerate) {
            generatedTaskId = asyncTaskId;
            ClozeQuiz quiz = new ClozeQuiz();
            quiz.setId(990L);
            return quiz;
        }
    }
}
