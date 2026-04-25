package com.suddenfix.order.domain.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CartItemVO {

    private Long productId;

    private String productName;

    private String mainImage;

    private Long price;

    private Integer quantity;

    private Boolean selected;

    private Boolean available;

    private Long subtotalAmount;

    private String unavailableReason;
}
