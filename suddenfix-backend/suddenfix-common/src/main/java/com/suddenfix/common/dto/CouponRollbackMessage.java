package com.suddenfix.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CouponRollbackMessage {

    private Long orderId;

    private Long couponId;

    private Long userId;

    private Integer segment;

    private String couponToken;
}
