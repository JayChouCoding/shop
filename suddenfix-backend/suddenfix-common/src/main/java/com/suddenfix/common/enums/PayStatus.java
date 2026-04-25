package com.suddenfix.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum PayStatus {

    PENDING_PAYMENT(0),
    PAYMENT_SUCCESS(1),
    PAYMENT_FAILED(2),
    REFUND(3);
    private final Integer status;
}
