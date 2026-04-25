package com.suddenfix.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum UserAccountTypeEnum {

    USERNAME(0,"用户名"),
    MOBILE(1,"手机号"),
    EMAIL(2,"邮箱");

    private final Integer code;
    private final String description;
}
