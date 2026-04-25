package com.suddenfix.product.domain.dto;

import lombok.Data;

@Data
public class ProductSearchRequest {

    private String keyword;

    private Long minPrice;

    private Long maxPrice;

    private Integer page = 1;

    private Integer size = 10;

    /**
     * SALES / LATEST
     */
    private String sortType = "SALES";
}
