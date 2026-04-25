package com.suddenfix.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum OrderStatic {

    INIT(0,"初始化"),
    PENDING_PAYMENT(10,"待支付"),
    PAID(20,"已支付"),
    SHIPPED(30,"已发货"),
    COMPLETED(40,"已完成"),
    CLOSED(50,"已关闭");

    private final Integer code;
    private final String description;
}
