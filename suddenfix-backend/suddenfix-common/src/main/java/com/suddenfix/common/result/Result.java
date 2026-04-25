package com.suddenfix.common.result;

import com.suddenfix.common.enums.ResultCodeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 全局统一返回结果类
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Result<T> {
    private Integer code;
    private String message;
    private T data;
    private Long timestamp;

    /**
     * 成功（无数据返回）
     */
    public static <T> Result<T> success(){
        return Result.<T>builder()
                .code(ResultCodeEnum.SUCCESS.getCode())
                .message(ResultCodeEnum.SUCCESS.getMessage())
                .data(null)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * 成功（有数据返回）
     */
    public static <T> Result<T> success(T data){
        return Result.<T>builder()
                .code(ResultCodeEnum.SUCCESS.getCode())
                .message(ResultCodeEnum.SUCCESS.getMessage())
                .data(data)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * 失败（使用默认的 500 系统异常返回）
     */
    public static <T> Result<T> fail(){
        return Result.<T>builder()
                .code(ResultCodeEnum.FAIL.getCode())
                .message(ResultCodeEnum.FAIL.getMessage())
                .data(null)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * 失败（根据传入的枚举状态码响应）
     */
    public static <T> Result<T> fail(ResultCodeEnum resultCodeEnum){
        return Result.<T>builder()
                .code(resultCodeEnum.getCode())
                .message(resultCodeEnum.getMessage())
                .data(null)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * 失败（根据传入的字符串响应）
     */
    public static <T> Result<T> fail(String message){
        return Result.<T>builder()
                .code(ResultCodeEnum.FAIL.getCode())
                .message(message)
                .data(null)
                .timestamp(System.currentTimeMillis())
                .build();
    }
}
