package com.lexiflow.common.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    SUCCESS(0, "ok", 200),

    UNAUTHORIZED(10001, "登录已过期，请重新登录", 401),
    BAD_REQUEST(10002, "参数校验失败", 400),
    CSRF_INVALID(10003, "CSRF Token 无效", 403),
    FORBIDDEN(10004, "权限不足", 403),

    INVALID_CREDENTIALS(11001, "邮箱或密码错误", 400),
    EMAIL_REGISTERED(11002, "邮箱已注册", 409),
    USER_DISABLED(11003, "用户已被禁用", 403),

    WORDBOOK_NOT_FOUND(20001, "词库不存在", 404),
    WORD_NOT_FOUND(20002, "单词不存在", 404),

    STUDY_PLAN_NOT_FOUND(30001, "学习计划不存在", 404),
    TODAY_TASK_NOT_FOUND(30002, "今日任务不存在或生成失败", 500),
    TASK_ITEM_NOT_SUBMITTABLE(30003, "当前任务项不可提交", 409),
    STUDY_PLAN_STATUS_INVALID(30004, "学习计划状态不可变更", 409),

    WRONG_WORD_NOT_FOUND(31001, "错词不存在", 404),
    FAVORITE_WORD_NOT_FOUND(31002, "收藏词不存在", 404),

    AI_CONFIG_UNAVAILABLE(40001, "AI 配置不可用", 400),
    AI_PUBLIC_QUOTA_EXHAUSTED(40002, "公共 AI 调用配额不足", 429),
    AI_CALL_FAILED(40003, "AI 调用失败", 500),

    CLOZE_QUIZ_NOT_FOUND(41001, "完形填空题目不存在", 404),
    CLOZE_ATTEMPT_SUBMITTED(41002, "完形填空已提交", 409),

    ASYNC_TASK_NOT_FOUND(50001, "异步任务不存在", 404),
    ASYNC_TASK_FAILED(50002, "异步任务执行失败", 500),

    NOT_FOUND(90004, "资源不存在", 404),
    CONFLICT(90009, "资源状态冲突", 409),
    TOO_MANY_REQUESTS(90029, "请求过于频繁", 429),
    INTERNAL_ERROR(90000, "系统异常", 500);

    private final int code;
    private final String message;
    private final int httpStatus;
}
