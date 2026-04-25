package com.suddenfix.order.domain.dto;

import lombok.Data;

@Data
public class CartAddRequest {

    private Long productId;

    private Integer quantity;
}
