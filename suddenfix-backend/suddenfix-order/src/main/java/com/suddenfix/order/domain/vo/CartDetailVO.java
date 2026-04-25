package com.suddenfix.order.domain.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class CartDetailVO {

    private List<CartItemVO> items;

    private Integer totalKinds;

    private Integer selectedKinds;

    private Long totalAmount;

    private Long selectedAmount;
}
