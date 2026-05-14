package com.lexiflow.async.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lexiflow.async.domain.AsyncTask;
import com.lexiflow.async.domain.AsyncTaskStatus;
import com.lexiflow.async.domain.AsyncTaskType;
import com.lexiflow.async.dto.AsyncTaskResponse;
import com.lexiflow.async.mapper.AsyncTaskMapper;
import com.lexiflow.common.error.ErrorCode;
import com.lexiflow.common.exception.BizException;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
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
        task.setRetryCount(0);
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

    public void markSuccess(Long taskId, Long resultId, String message) {
        AsyncTask task = asyncTaskMapper.selectById(taskId);
        task.setStatus(AsyncTaskStatus.SUCCESS);
        task.setProgress(100);
        task.setResultId(resultId);
        task.setMessage(message);
        task.setFinishedAt(LocalDateTime.now());
        asyncTaskMapper.updateById(task);
    }

    public void markFailed(Long taskId, String errorCode, String errorMessage) {
        AsyncTask task = asyncTaskMapper.selectById(taskId);
        task.setStatus(AsyncTaskStatus.FAILED);
        task.setMessage("任务执行失败");
        task.setErrorCode(errorCode);
        task.setErrorMessage(abbreviate(errorMessage));
        task.setFinishedAt(LocalDateTime.now());
        asyncTaskMapper.updateById(task);
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