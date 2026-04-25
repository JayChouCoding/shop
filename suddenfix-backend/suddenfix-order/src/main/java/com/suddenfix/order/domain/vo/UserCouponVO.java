package com.suddenfix.order.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserCouponVO {

    private Long couponId;

    private String name;

    private BigDecimal amount;

    private BigDecimal minPoint;

    private Integer segment;

    private String couponToken;

    private Date startTime;

    private Date endTime;

    private Boolean available;

    private String unavailableReason;

    private Long estimatedDiscountAmount;

    private Long orderAmount;
}
