package com.suddenfix.product.controller;

import com.suddenfix.product.domain.dto.ProductSearchRequest;
import com.suddenfix.common.dto.ProductSkuDTO;
import com.suddenfix.common.result.Result;
import com.suddenfix.product.domain.pojo.Product;
import com.suddenfix.product.domain.pojo.ProductDoc;
import com.suddenfix.product.domain.vo.ProductDetailVO;
import com.suddenfix.product.domain.vo.ProductSearchItemVO;
import com.suddenfix.product.domain.vo.ProductSearchResponseVO;
import com.suddenfix.product.service.IProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@RestController
@RequestMapping("/product")
@RequiredArgsConstructor
public class ProductController {

    private final IProductService productService;

    @GetMapping("/detail/{id}")
    public Result<ProductDetailVO> getProductDetailById(@PathVariable Long id) {
        return productService.getProductDetailById(id);
    }

    @GetMapping("/image/view/{fileName:.+}")
    public ResponseEntity<Resource> viewUploadedImage(@PathVariable String fileName) throws MalformedURLException {
        Path imagePath = Path.of(System.getProperty("user.dir"), "uploads", "products", fileName).normalize();
        if (!Files.exists(imagePath) || !imagePath.startsWith(Path.of(System.getProperty("user.dir"), "uploads", "products"))) {
            return ResponseEntity.notFound().build();
        }
        Resource resource = new UrlResource(imagePath.toUri());
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        try {
            String detectedType = Files.probeContentType(imagePath);
            if (detectedType != null) {
                mediaType = MediaType.parseMediaType(detectedType);
            }
        } catch (Exception ignored) {
        }
        return ResponseEntity.ok().contentType(mediaType).body(resource);
    }

    @GetMapping("/list")
    public Result<List<ProductSearchItemVO>> listProducts(@RequestParam(value = "keyword", required = false) String keyword,
                                                          @RequestParam(value = "page", required = false) Integer page,
                                                          @RequestParam(value = "size", required = false) Integer size) {
        return productService.listProducts(keyword, page, size);
    }

    @PostMapping("/add")
    public Result<Long> addProduct(@RequestBody Product product) {
        return productService.addProduct(product);
    }

    @PutMapping("/update")
    public Result<Void> updateProduct(@RequestBody Product product) {
        return productService.updateProduct(product);
    }

    @PostMapping("/preheat/{id}")
    public Result<Long> preheatProduct(@PathVariable("id") Long productId) {
        return productService.preheatProduct(productId);
    }

    @PostMapping("/batch")
    public Result<List<ProductSkuDTO>> batchQueryProducts(@RequestBody List<Long> productIds) {
        return productService.batchQueryProducts(productIds);
    }

    @GetMapping("/search")
    public Result<Page<ProductDoc>> searchProduct(@RequestParam("keyword") String keyword,
                                                  @RequestParam(value = "page",defaultValue = "1") int page,
                                                  @RequestParam(value = "size",defaultValue = "10") int size) {
        return productService.searchProduct(keyword,page,size);
    }

    @PostMapping("/search/advanced")
    public Result<ProductSearchResponseVO> searchProduct(@RequestBody ProductSearchRequest request) {
        return productService.searchProduct(request);
    }
}
