package com.lexiflow.common.api;

import com.lexiflow.common.error.ErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 统一接口响应封装类。
 * <p>
 * 所有 API 接口的返回值均使用此类进行包装，包含业务状态码、响应消息和数据体。
 * 提供静态工厂方法 {@link #success(Object)} 和 {@link #fail(ErrorCode)} 便于构造。
 * </p>
 *
 * @param <T> 响应数据的泛型类型
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "统一接口响应")
public class ApiResponse<T> {

    @Schema(description = "业务状态码", example = "0")
    private int code;

    @Schema(description = "响应消息", example = "success")
    private String message;

    @Schema(description = "响应数据")
    private T data;

    /**
     * 返回成功响应，携带数据。
     *
     * @param data 响应数据
     * @param <T>  数据类型
     * @return 成功的 ApiResponse
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMessage(), data);
    }

    /**
     * 返回成功响应，不携带数据。
     *
     * @return 成功的 ApiResponse（data 为 null）
     */
    public static ApiResponse<Void> success() {
        return success(null);
    }

    /**
     * 根据错误码返回失败响应。
     *
     * @param errorCode 业务错误码枚举
     * @return 失败的 ApiResponse
     */
    public static ApiResponse<Void> fail(ErrorCode errorCode) {
        return fail(errorCode.getCode(), errorCode.getMessage());
    }

    /**
     * 根据自定义状态码和消息返回失败响应。
     *
     * @param code    业务状态码
     * @param message 错误消息
     * @return 失败的 ApiResponse
     */
    public static ApiResponse<Void> fail(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}
