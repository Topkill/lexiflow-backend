package com.lexiflow.async.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lexiflow.async.domain.AsyncTask;
import com.lexiflow.async.domain.AsyncTaskStatus;
import com.lexiflow.async.mapper.AsyncTaskMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AsyncTaskServiceTest {

    @Mock
    private AsyncTaskMapper asyncTaskMapper;

    @InjectMocks
    private AsyncTaskService asyncTaskService;

    @Test
    void markSuccessShouldReturnTrueWhenRunningTaskIsUpdated() {
        when(asyncTaskMapper.update(any(AsyncTask.class), any())).thenReturn(1);

        boolean updated = asyncTaskService.markSuccess(10L, 20L, "完成");

        assertThat(updated).isTrue();
        ArgumentCaptor<AsyncTask> taskCaptor = ArgumentCaptor.forClass(AsyncTask.class);
        verify(asyncTaskMapper).update(taskCaptor.capture(), any());
        AsyncTask update = taskCaptor.getValue();
        assertThat(update.getStatus()).isEqualTo(AsyncTaskStatus.SUCCESS);
        assertThat(update.getProgress()).isEqualTo(100);
        assertThat(update.getResultId()).isEqualTo(20L);
        assertThat(update.getMessage()).isEqualTo("完成");
        assertThat(update.getFinishedAt()).isNotNull();
    }

    @Test
    void markFailedShouldReturnFalseWhenRunningTaskIsNotUpdated() {
        when(asyncTaskMapper.update(any(AsyncTask.class), any())).thenReturn(0);

        boolean updated = asyncTaskService.markFailed(10L, "50002", "失败");

        assertThat(updated).isFalse();
    }

    @Test
    void markPendingFailedShouldWriteFailureFields() {
        when(asyncTaskMapper.update(any(AsyncTask.class), any())).thenReturn(1);

        boolean updated = asyncTaskService.markPendingFailed(10L, "50002", "入队失败");

        assertThat(updated).isTrue();
        ArgumentCaptor<AsyncTask> taskCaptor = ArgumentCaptor.forClass(AsyncTask.class);
        verify(asyncTaskMapper).update(taskCaptor.capture(), any());
        AsyncTask update = taskCaptor.getValue();
        assertThat(update.getStatus()).isEqualTo(AsyncTaskStatus.FAILED);
        assertThat(update.getMessage()).isEqualTo("任务执行失败");
        assertThat(update.getErrorCode()).isEqualTo("50002");
        assertThat(update.getErrorMessage()).isEqualTo("入队失败");
        assertThat(update.getFinishedAt()).isNotNull();
    }
}
