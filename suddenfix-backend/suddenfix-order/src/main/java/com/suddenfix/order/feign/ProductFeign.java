package com.suddenfix.order.feign;

import com.suddenfix.common.dto.ProductSkuDTO;
import com.suddenfix.common.result.Result;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.cloud.openfeign.FeignClient;

import java.util.List;

@FeignClient(value = "suddenfix-product")
public interface ProductFeign {

    @PostMapping("/product/batch")
    Result<List<ProductSkuDTO>> getProductsByIds(@RequestBody List<Long> productIds);
}
