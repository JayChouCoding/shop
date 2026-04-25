package com.suddenfix.order.domain.dto;

import lombok.Data;

@Data
public class CartUpdateRequest {

    private Long productId;

    private Integer quantity;
}
