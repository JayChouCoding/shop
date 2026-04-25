package com.suddenfix.order.domain.pojo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class OrderWithProduct extends Order {

    private List<Long> productIds;

    private List<Long> productQuantities;

    private Long couponId;

    private Integer couponSegment;

    private String couponToken;
}
