package com.lexiflow.common.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    SUCCESS("0", "success"),
    BAD_REQUEST("400", "请求参数错误"),
    UNAUTHORIZED("401", "请先登录"),
    FORBIDDEN("403", "无权访问"),
    NOT_FOUND("404", "资源不存在"),
    CONFLICT("409", "资源状态冲突"),
    TOO_MANY_REQUESTS("429", "请求过于频繁"),
    INTERNAL_ERROR("500", "系统繁忙，请稍后再试");

    private final String code;
    private final String message;
}
