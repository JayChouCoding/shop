package com.suddenfix.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Duration;

@Getter
@AllArgsConstructor
public enum RedisExpirationTime {
    EXPIRATION_TIME(Duration.ofMinutes(5));

    private final Duration timeout;
}
