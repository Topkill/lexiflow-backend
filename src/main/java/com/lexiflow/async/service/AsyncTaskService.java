package com.lexiflow.async.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.lexiflow.async.domain.AsyncTask;
import com.lexiflow.async.domain.AsyncTaskStatus;
import com.lexiflow.async.domain.AsyncTaskType;
import com.lexiflow.async.dto.AsyncTaskResponse;
import com.lexiflow.async.mapper.AsyncTaskMapper;
import com.lexiflow.common.error.ErrorCode;
import com.lexiflow.common.exception.BizException;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AsyncTaskService {

    private final AsyncTaskMapper asyncTaskMapper;

    public AsyncTask createTask(Long userId, AsyncTaskType taskType, String requestJson) {
        AsyncTask task = new AsyncTask();
        task.setUserId(userId);
        task.setTaskType(taskType);
        task.setStatus(AsyncTaskStatus.PENDING);
        task.setProgress(0);
        task.setMessage("等待执行");
        task.setRequestJson(requestJson);
        asyncTaskMapper.insert(task);
        return task;
    }

    public void markRunning(Long taskId, String message, int progress) {
        AsyncTask task = asyncTaskMapper.selectById(taskId);
        task.setStatus(AsyncTaskStatus.RUNNING);
        task.setMessage(message);
        task.setProgress(progress);
        task.setStartedAt(LocalDateTime.now());
        asyncTaskMapper.updateById(task);
    }

    public boolean markRunningIfPending(Long taskId, String message, int progress) {
        AsyncTask task = new AsyncTask();
        task.setStatus(AsyncTaskStatus.RUNNING);
        task.setMessage(message);
        task.setProgress(progress);
        task.setStartedAt(LocalDateTime.now());
        return asyncTaskMapper.update(task, new LambdaUpdateWrapper<AsyncTask>()
                .eq(AsyncTask::getId, taskId)
                .eq(AsyncTask::getStatus, AsyncTaskStatus.PENDING)) > 0;
    }

    public boolean markSuccess(Long taskId, Long resultId, String message) {
        AsyncTask task = new AsyncTask();
        task.setStatus(AsyncTaskStatus.SUCCESS);
        task.setProgress(100);
        task.setResultId(resultId);
        task.setMessage(message);
        task.setFinishedAt(LocalDateTime.now());
        boolean updated = asyncTaskMapper.update(task, new LambdaUpdateWrapper<AsyncTask>()
                .eq(AsyncTask::getId, taskId)
                .eq(AsyncTask::getStatus, AsyncTaskStatus.RUNNING)) > 0;
        if (!updated) {
            log.warn("忽略异步任务成功终态更新，任务不是 RUNNING，taskId={}", taskId);
        }
        return updated;
    }

    public boolean markFailed(Long taskId, String errorCode, String errorMessage) {
        return markFailedFromStatus(taskId, AsyncTaskStatus.RUNNING, errorCode, errorMessage);
    }

    public boolean markPendingFailed(Long taskId, String errorCode, String errorMessage) {
        return markFailedFromStatus(taskId, AsyncTaskStatus.PENDING, errorCode, errorMessage);
    }

    private boolean markFailedFromStatus(Long taskId, AsyncTaskStatus expectedStatus, String errorCode, String errorMessage) {
        AsyncTask task = new AsyncTask();
        task.setStatus(AsyncTaskStatus.FAILED);
        task.setMessage("任务执行失败");
        task.setErrorCode(errorCode);
        task.setErrorMessage(abbreviate(errorMessage));
        task.setFinishedAt(LocalDateTime.now());
        boolean updated = asyncTaskMapper.update(task, new LambdaUpdateWrapper<AsyncTask>()
                .eq(AsyncTask::getId, taskId)
                .eq(AsyncTask::getStatus, expectedStatus)) > 0;
        if (!updated) {
            log.warn("忽略异步任务失败终态更新，任务不是 {}，taskId={}", expectedStatus, taskId);
        }
        return updated;
    }

    public AsyncTask getOwnedTaskEntity(Long userId, Long taskId) {
        AsyncTask task = asyncTaskMapper.selectOne(new LambdaQueryWrapper<AsyncTask>()
                .eq(AsyncTask::getId, taskId)
                .eq(AsyncTask::getUserId, userId)
                .last("LIMIT 1"));
        if (task == null) {
            throw new BizException(ErrorCode.ASYNC_TASK_NOT_FOUND);
        }
        return task;
    }

    public AsyncTask getTaskEntity(Long taskId) {
        return asyncTaskMapper.selectById(taskId);
    }

    public List<AsyncTask> listRecentTasks(Long userId, AsyncTaskType taskType, int limit) {
        return asyncTaskMapper.selectList(new LambdaQueryWrapper<AsyncTask>()
                .eq(AsyncTask::getUserId, userId)
                .eq(AsyncTask::getTaskType, taskType)
                .orderByDesc(AsyncTask::getId)
                .last("LIMIT " + Math.max(1, limit)));
    }

    public AsyncTaskResponse getTask(Long userId, Long taskId) {
        return AsyncTaskResponse.from(getOwnedTaskEntity(userId, taskId));
    }

    private String abbreviate(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        return message.length() > 1024 ? message.substring(0, 1024) : message;
    }
}
