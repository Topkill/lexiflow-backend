package com.lexiflow.ai.core.client;

/**
 * AI 客户端异常
 * <p>
 * 封装 AI 服务调用过程中发生的异常，包含错误码和错误信息。
 * 错误码可来源于 HTTP 状态码或自定义错误标识。
 * </p>
 */
public class AiClientException extends RuntimeException {

    private final String errorCode;

    public AiClientException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public AiClientException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
