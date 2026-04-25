package com.suddenfix.order.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
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
public class OrderCouponVO {

    private Long couponId;

    private String name;

    private BigDecimal amount;

    private BigDecimal minPoint;

    private Integer segment;

    private String couponToken;

    private Integer status;

    private Long discountAmount;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date usedTime;
}
