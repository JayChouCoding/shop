package com.suddenfix.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum BloomFilterInit {

    DATA_SPLIT_NUMBER("5000"),
    EXPECTED_INSERTIONS("1000000"),
    FALSE_PROBABILITY("0.01"),
    USER_BLOOM_FILTER("bloom:user:ids");
    private final String value;
}
