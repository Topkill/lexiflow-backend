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
    STUDY_PLAN_STATUS_INVALID(30004, "学习计划状态不可变更", 409),

    NOT_FOUND(90004, "资源不存在", 404),
    CONFLICT(90009, "资源状态冲突", 409),
    TOO_MANY_REQUESTS(90029, "请求过于频繁", 429),
    INTERNAL_ERROR(90000, "系统异常", 500);

    private final int code;
    private final String message;
    private final int httpStatus;
}
