package com.suddenfix.product.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductSearchResponseVO {

    private Long total;

    private Integer page;

    private Integer size;

    private List<ProductSearchItemVO> records;
}
