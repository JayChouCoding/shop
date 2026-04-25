package com.suddenfix.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum Exist {

    IS_EXIST(1),
    IS_NOT_EXIST(0);
    private final Integer isExist;
}
