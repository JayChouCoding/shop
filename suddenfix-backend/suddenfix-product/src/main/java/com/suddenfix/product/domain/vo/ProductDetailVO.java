package com.suddenfix.product.domain.vo;

import com.suddenfix.product.domain.pojo.Product;
import com.suddenfix.product.domain.pojo.ProductDoc;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductDetailVO {

    private Product product; // 核心商品信息
    private List<ProductDoc> recommendProduct; // 异步查出的同类推荐商品
}
