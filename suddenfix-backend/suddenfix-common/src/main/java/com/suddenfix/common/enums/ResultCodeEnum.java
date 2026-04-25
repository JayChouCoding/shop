package com.suddenfix.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 全局统一状态码枚举
 */
@Getter
@AllArgsConstructor
public enum ResultCodeEnum {

    SUCCESS(200,"操作成功"),
    FAIL(500,"系统异常"),
    UNAUTHORIZED(401,"暂未登陆"),
    GATEWAY_ERROR(503,"网关层异常:"),

    PASSWORD_Missing(10000,"密码不能为空"),
    USERNAME_IS_EXIST(10001,"用户名已经存在"),
    INSERT_USER_ERROR(10002,"新增用户失败"),
    PASSWORD_ERROR(10003,"密码错误"),
    USER_MISSING(10004,"未找到相应用户"),
    ORDER_IS_EXIST(10005,"正在创建订单，请勿重复点击"),
    INSERT_ORDER_ERROR(10006,"插入订单失败"),
    INSERT_MSG_ERROR(10007,"插入消息表失败"),
    INSERT_ORDER_ITEM_ERROR(10008,"插入订单详情失败");


    private final Integer code;
    private final String message;

}
