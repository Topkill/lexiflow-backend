package com.lexiflow.common.handler;

import com.lexiflow.common.api.ApiResponse;
import com.lexiflow.common.error.ErrorCode;
import com.lexiflow.common.exception.BizException;
import jakarta.validation.ConstraintViolationException;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理器。
 * <p>
 * 统一捕获并处理各类异常，将其转换为标准的 {@link ApiResponse} 格式响应。
 * 处理的异常类型包括：
 * <ul>
 *   <li>{@link BizException} - 业务异常，返回对应错误码和 HTTP 状态</li>
 *   <li>{@link MethodArgumentNotValidException} / {@link BindException} - 参数绑定校验失败</li>
 *   <li>{@link ConstraintViolationException} - 约束校验失败</li>
 *   <li>{@link HttpMessageNotReadableException} - 请求体不可读</li>
 *   <li>{@link MaxUploadSizeExceededException} - 文件上传超限</li>
 *   <li>{@link NoHandlerFoundException} / {@link NoResourceFoundException} - 资源不存在</li>
 *   <li>{@link Exception} - 其他未预期异常</li>
 * </ul>
 * </p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 处理业务异常，返回对应错误码和 HTTP 状态。 */
    @ExceptionHandler(BizException.class)
    public ResponseEntity<ApiResponse<Void>> handleBizException(BizException ex) {
        HttpStatus status = HttpStatus.valueOf(ex.getErrorCode().getHttpStatus());
        return ResponseEntity.status(status)
                .body(ApiResponse.fail(ex.getErrorCode().getCode(), ex.getCustomMessage()));
    }

    /** 处理参数绑定校验失败异常（@RequestBody 和表单绑定）。 */
    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<ApiResponse<Void>> handleBindException(Exception ex) {
        String message;
        if (ex instanceof MethodArgumentNotValidException validException) {
            message = validException.getBindingResult().getFieldErrors().stream()
                    .map(error -> error.getField() + " " + error.getDefaultMessage())
                    .collect(Collectors.joining("; "));
        } else if (ex instanceof BindException bindException) {
            message = bindException.getBindingResult().getFieldErrors().stream()
                    .map(error -> error.getField() + " " + error.getDefaultMessage())
                    .collect(Collectors.joining("; "));
        } else {
            message = ErrorCode.BAD_REQUEST.getMessage();
        }
        return ResponseEntity.badRequest().body(ApiResponse.fail(ErrorCode.BAD_REQUEST.getCode(), message));
    }

    /** 处理约束校验失败异常（@Validated 路径参数等）。 */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolationException(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().body(ApiResponse.fail(ErrorCode.BAD_REQUEST.getCode(), message));
    }

    /** 处理请求体不可读异常（如 JSON 格式错误）。 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadableException(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.fail(ErrorCode.BAD_REQUEST));
    }

    /** 处理文件上传超过大小限制的异常。 */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.fail(ErrorCode.FILE_TOO_LARGE));
    }

    /** 处理资源不存在异常（404）。 */
    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ApiResponse<Void>> handleNotFoundException(Exception ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.fail(ErrorCode.NOT_FOUND));
    }

    /** 处理所有未预期的异常，记录日志并返回 500 错误。 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail(ErrorCode.INTERNAL_ERROR));
    }
}
