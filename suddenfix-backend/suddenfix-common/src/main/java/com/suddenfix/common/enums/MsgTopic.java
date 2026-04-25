package com.suddenfix.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum MsgTopic {

    TOPIC_PAY_SUCCESS("TOPIC_PAY_SUCCESS"),
    TOPIC_USER_CANCELLED("TOPIC_USER_CANCELLED"),
    TOPIC_TO_AGENT("TOPIC_TO_AGENT"),
    TOPIC_ON_CREATE("TOPIC_ON_CREATE"),
    TOPIC_STOCK_DEDUCTION("TOPIC_STOCK_DEDUCTION"),
    TOPIC_RESTORE_STOCK("TOPIC_RESTORE_STOCK"),
    TOPIC_ORDER_CANCEL_COMPENSATE("TOPIC_ORDER_CANCEL_COMPENSATE"),
    TOPIC_ORDER_CREATED("TOPIC_ORDER_CREATED"),
    TOPIC_PAY_CREATED("TOPIC_PAY_CREATED"),
    TOPIC_ORDER_CANCEL("TOPIC_ORDER_CANCEL"),
    TOPIC_ORDER_PAID("TOPIC_ORDER_PAID"),
    TOPIC_COUPON_ROLLBACK("TOPIC_COUPON_ROLLBACK"),
    TOPIC_PAY_REFUND("TOPIC_PAY_REFUND");
    private final String topic;

    public static final String TOPIC_PAY_SUCCESS_NAME = "TOPIC_PAY_SUCCESS";
    public static final String TOPIC_USER_CANCELLED_NAME = "TOPIC_USER_CANCELLED";
    public static final String TOPIC_TO_AGENT_NAME = "TOPIC_TO_AGENT";
    public static final String TOPIC_ON_CREATE_NAME = "TOPIC_ON_CREATE";
    public static final String TOPIC_STOCK_DEDUCTION_NAME = "TOPIC_STOCK_DEDUCTION";
    public static final String TOPIC_RESTORE_STOCK_NAME = "TOPIC_RESTORE_STOCK";
    public static final String TOPIC_ORDER_CANCEL_COMPENSATE_NAME = "TOPIC_ORDER_CANCEL_COMPENSATE";
    public static final String TOPIC_ORDER_CREATED_NAME = "TOPIC_ORDER_CREATED";
    public static final String TOPIC_PAY_CREATED_NAME = "TOPIC_PAY_CREATED";
    public static final String TOPIC_ORDER_CANCEL_NAME = "TOPIC_ORDER_CANCEL";
    public static final String TOPIC_ORDER_PAID_NAME = "TOPIC_ORDER_PAID";
    public static final String TOPIC_COUPON_ROLLBACK_NAME = "TOPIC_COUPON_ROLLBACK";
    public static final String TOPIC_PAY_REFUND_NAME = "TOPIC_PAY_REFUND";
}
