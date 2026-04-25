package com.suddenfix.product.controller;

import com.suddenfix.common.exception.ServiceException;
import com.suddenfix.common.result.Result;
import com.suddenfix.product.domain.dto.AdminProductUploadRequest;
import com.suddenfix.product.domain.vo.AdminProductUploadResponse;
import com.suddenfix.product.domain.vo.ProductImageUploadResponse;
import com.suddenfix.product.service.IProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/admin/product")
@RequiredArgsConstructor
public class AdminProductController {

    private final IProductService productService;

    @PostMapping("/upload")
    public Result<AdminProductUploadResponse> uploadFlashSaleProduct(@RequestBody AdminProductUploadRequest request,
                                                                     @RequestHeader(value = "role", required = false) Integer role) {
        validateAdminRole(role);
        return productService.adminUploadProduct(request);
    }

    @PostMapping(value = "/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<ProductImageUploadResponse> uploadProductImage(@RequestPart("file") MultipartFile file,
                                                                 @RequestHeader(value = "role", required = false) Integer role) {
        validateAdminRole(role);
        return productService.uploadProductImage(file);
    }

    private void validateAdminRole(Integer role) {
        if (role == null || role != 1) {
            throw new ServiceException("仅商家账号可使用该接口");
        }
    }
}
