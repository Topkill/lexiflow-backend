package com.lexiflow.common.exception;

import com.lexiflow.common.error.ErrorCode;
import lombok.Getter;

/**
 * 业务异常类。
 * <p>
 * 用于表示业务逻辑中可预期的异常场景，携带 {@link ErrorCode} 枚举信息。
 * 由 {@link com.lexiflow.common.handler.GlobalExceptionHandler} 统一捕获并转换为标准响应。
 * </p>
 */
@Getter
public class BizException extends RuntimeException {

    /** 业务错误码枚举 */
    private final ErrorCode errorCode;

    /** 自定义错误消息（可能与 ErrorCode 默认消息不同） */
    private final String customMessage;

    /**
     * 使用错误码的默认消息构造业务异常。
     *
     * @param errorCode 业务错误码
     */
    public BizException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.customMessage = errorCode.getMessage();
    }

    /**
     * 使用自定义消息构造业务异常。
     *
     * @param errorCode 业务错误码
     * @param message   自定义错误消息
     */
    public BizException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.customMessage = message;
    }
}
