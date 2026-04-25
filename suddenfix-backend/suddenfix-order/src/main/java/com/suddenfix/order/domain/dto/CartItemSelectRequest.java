package com.suddenfix.order.domain.dto;

import lombok.Data;

@Data
public class CartItemSelectRequest {

    private Long productId;

    private Boolean selected;
}
