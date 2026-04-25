package com.suddenfix.product.service;

import com.suddenfix.common.dto.ProductSkuDTO;
import com.suddenfix.common.result.Result;
import com.suddenfix.product.domain.dto.AdminProductUploadRequest;
import com.suddenfix.product.domain.dto.ProductSearchRequest;
import com.suddenfix.product.domain.pojo.Product;
import com.suddenfix.product.domain.pojo.ProductDoc;
import com.suddenfix.product.domain.vo.AdminProductUploadResponse;
import com.suddenfix.product.domain.vo.ProductImageUploadResponse;
import com.suddenfix.product.domain.vo.ProductSearchItemVO;
import com.suddenfix.product.domain.vo.ProductSearchResponseVO;
import com.suddenfix.product.domain.vo.ProductDetailVO;
import org.springframework.data.domain.Page;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface IProductService {
    Result<AdminProductUploadResponse> adminUploadProduct(AdminProductUploadRequest request);

    Result<ProductImageUploadResponse> uploadProductImage(MultipartFile file);

    Result<ProductDetailVO> getProductDetailById(Long id);

    Result<Long> addProduct(Product product);

    Result<Void> updateProduct(Product product);

    Result<Long> preheatProduct(Long productId);

    Result<List<ProductSkuDTO>> batchQueryProducts(List<Long> productIds);

    Result<Page<ProductDoc>> searchProduct(String keyword, int page, int size);

    Result<ProductSearchResponseVO> searchProduct(ProductSearchRequest request);

    Result<List<ProductSearchItemVO>> listProducts(String keyword, Integer page, Integer size);
}
