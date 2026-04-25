package com.suddenfix.common.exception;

import com.suddenfix.common.enums.ResultCodeEnum;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 全局自定义业务异常
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ServiceException extends RuntimeException {

    private Integer code;
    private String message;

    /**
     * 通过状态码枚举构造异常
     */
    public ServiceException(ResultCodeEnum resultCodeEnum) {
        super(resultCodeEnum.getMessage());
        this.code = resultCodeEnum.getCode();
        this.message = resultCodeEnum.getMessage();
    }

    public ServiceException(String message) {
        super(message);
        this.message = message;
    }
}
