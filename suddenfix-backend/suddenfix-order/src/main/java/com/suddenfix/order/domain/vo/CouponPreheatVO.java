package com.suddenfix.order.domain.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CouponPreheatVO {

    private Long couponId;

    private Integer segment;

    private Long userId;

    private String couponToken;
}
