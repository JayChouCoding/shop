package com.suddenfix.order.domain.dto;

import lombok.Data;

import java.util.List;

@Data
public class CartCheckoutClearRequest {

    private List<Long> productIds;
}
