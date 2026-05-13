package com.lexiflow.ai.core.client;

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
