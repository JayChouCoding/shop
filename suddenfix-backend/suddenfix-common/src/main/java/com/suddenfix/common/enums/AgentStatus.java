package com.suddenfix.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum AgentStatus {

    THINKING(0,"思考中"),
    COMPLETED(1,"已完成"),
    FAILED(2,"失败");
    private final Integer status;
    private final String message;
}
