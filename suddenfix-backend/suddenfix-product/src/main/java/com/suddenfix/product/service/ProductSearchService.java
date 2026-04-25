package com.suddenfix.product.service;

import com.suddenfix.product.domain.dto.ProductSearchRequest;
import com.suddenfix.product.domain.vo.ProductSearchResponseVO;

public interface ProductSearchService {

    ProductSearchResponseVO search(ProductSearchRequest request);
}
