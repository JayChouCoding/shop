package com.suddenfix.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum MsgStatus {

    PENDING_SENDING(0,"待发送"),
    ALREADY_SENT(1,"已发送"),
    FAILED_SEND(2,"发送失败");

    private final Integer status;
    private final String message;
}
