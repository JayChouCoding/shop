package com.suddenfix.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum RedisPreMessage {

    USERNAME_ID_MAPPING("suddenfix:mapping:username:id:"),
    REDIS_PREVENT_DUPLICATION("suddenfix:prevent:duplicate:"),
    PHONE_ID_MAPPING("suddenfix:mapping:phone:id:"),
    EMAIL_ID_MAPPING("suddenfix:mapping:email:id:"),
    ORDER_SCHEDULE("suddenfix:order:schedule"),
    PAY_SCHEDULE("suddenfix:pay:schedule"),
    PAY_IDEMPOTENT("suddenfix:pay:idempotent:"),
    PAY_NOTIFY_LOCKED("suddenfix:pay:notify:locked:"),
    TOKEN_BLACKLIST("suddenfix:token:blacklist:"),
    USER_ADDRESS("suddenfix:user:address:"),
    PRODUCT_DETAIL("suddenfix:product:detail:"),
    PRODUCT_SEARCH("suddenfix:product:search:"),
    PRODUCT_RECOMMEND("suddenfix:product:recommend:"),
    GOODS_PRE_DEDUCTION("suddenfix:goods:prededuction:"),
    GOODS_IS_EXIST("suddenfix:goods:exist:"),
    ALREADY_USED_NAME("suddenfix:user:register:"),
    CART("suddenfix:cart:"),
    CART_SELECTED("suddenfix:cart:selected:"),
    COUPON_STOCK_SEGMENT("suddenfix:coupon:stock:"),
    COUPON_USER_SET("suddenfix:coupon:users:"),
    COUPON_USER_TOKEN_HASH("suddenfix:coupon:user:token:"),
    COUPON_BITMAP("suddenfix:coupon:bitmap:"),
    COUPON_META("suddenfix:coupon:meta:"),
    COUPON_IS_EXIST("suddenfix:coupon:exist:"),
    COUPON_ROLLBACK_IDEMPOTENT("suddenfix:coupon:rollback:"),
    PRODUCT_DB_DEDUCTED("suddenfix:product:db:deducted:"),
    PRODUCT_RESTORE_IDEMPOTENT("suddenfix:product:restore:idempotent:"),
    ORDER_PAY_COMPENSATE("suddenfix:order:pay:compensate:"),
    ORDER_STOCK_RESTORED("suddenfix:order:stock:restored:");
    private final String value;
}
