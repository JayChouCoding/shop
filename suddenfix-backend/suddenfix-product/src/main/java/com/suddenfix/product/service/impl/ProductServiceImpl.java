package com.suddenfix.product.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.suddenfix.common.dto.ProductSkuDTO;
import com.suddenfix.common.exception.ServiceException;
import com.suddenfix.common.result.Result;
import com.suddenfix.product.domain.dto.AdminProductUploadRequest;
import com.suddenfix.product.domain.dto.ProductSearchRequest;
import com.suddenfix.product.domain.pojo.Product;
import com.suddenfix.product.domain.pojo.ProductDoc;
import com.suddenfix.product.domain.vo.AdminProductUploadResponse;
import com.suddenfix.product.domain.vo.ProductDetailVO;
import com.suddenfix.product.domain.vo.ProductImageUploadResponse;
import com.suddenfix.product.domain.vo.ProductSearchItemVO;
import com.suddenfix.product.domain.vo.ProductSearchResponseVO;
import com.suddenfix.product.mapper.ProductMapper;
import com.suddenfix.product.service.IProductService;
import com.suddenfix.product.service.ProductSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.suddenfix.common.enums.RedisPreMessage.GOODS_IS_EXIST;
import static com.suddenfix.common.enums.RedisPreMessage.GOODS_PRE_DEDUCTION;
import static com.suddenfix.common.enums.RedisPreMessage.PRODUCT_PREHEAT_HASH;
import static com.suddenfix.common.enums.RedisPreMessage.PRODUCT_DETAIL;
import static com.suddenfix.common.enums.RedisPreMessage.PRODUCT_RECOMMEND;
import static com.suddenfix.common.enums.RedisPreMessage.PRODUCT_SEARCH;

@Service
@Slf4j
@RequiredArgsConstructor
public class ProductServiceImpl implements IProductService {

    private static final String DEFAULT_PRODUCT_IMAGE = "https://placehold.co/600x600/png?text=SuddenFix";
    private static final String PRODUCT_IMAGE_PUBLIC_PREFIX = "/api/product/image/view/";
    private static final long PRODUCT_DETAIL_TTL_MINUTES = 30L;

    private final ProductMapper productMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ThreadPoolExecutor threadPoolExecutor;
    private final ProductSearchService productSearchService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<AdminProductUploadResponse> adminUploadProduct(AdminProductUploadRequest request) {
        normalizeUploadRequest(request);
        validateUploadRequest(request);

        Date now = new Date();
        Product product = Product.builder()
                .name(request.getName())
                .categoryId(request.getCategoryId())
                .mainImage(request.getMainImage())
                .price(request.getPrice())
                .stock(request.getStock())
                .description(request.getDescription())
                .status(request.getStatus() == null ? 1 : request.getStatus())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .createTime(now)
                .updateTime(now)
                .build();

        productMapper.insertProduct(product);
        Product savedProduct = productMapper.selectById(product.getId());
        evictProductCaches(product.getId());
        syncProductStockCache(savedProduct == null ? product : savedProduct);

        Product resultProduct = savedProduct == null ? product : savedProduct;
        return Result.success(buildUploadResponse(resultProduct));
    }

    @Override
    public Result<ProductImageUploadResponse> uploadProductImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ServiceException("请先选择要上传的商品图片");
        }
        String originalFilename = file.getOriginalFilename();
        String extension = extractExtension(originalFilename);
        if (!List.of(".jpg", ".jpeg", ".png", ".webp", ".gif").contains(extension.toLowerCase())) {
            throw new ServiceException("仅支持 jpg、jpeg、png、webp、gif 格式图片");
        }

        Path uploadDir = resolveUploadDir();
        String fileName = UUID.randomUUID().toString().replace("-", "") + extension.toLowerCase();
        Path target = uploadDir.resolve(fileName);
        try {
            Files.createDirectories(uploadDir);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new ServiceException("商品图片上传失败");
        }

        return Result.success(ProductImageUploadResponse.builder()
                .fileName(fileName)
                .imageUrl(PRODUCT_IMAGE_PUBLIC_PREFIX + fileName)
                .size(file.getSize())
                .build());
    }

    @Override
    public Result<ProductDetailVO> getProductDetailById(Long id) {
        ProductDetailVO productDetailVO = new ProductDetailVO();
        CompletableFuture<Product> infoFuture = CompletableFuture.supplyAsync(() -> loadProductDetail(id), threadPoolExecutor);
        CompletableFuture<Void> recommendFuture = infoFuture.thenAcceptAsync(product -> {
            if (product != null && product.getId() != null) {
                productDetailVO.setRecommendProduct(loadRecommendProducts(product.getCategoryId(), product.getId()));
            }
        }, threadPoolExecutor);

        try {
            CompletableFuture.allOf(infoFuture, recommendFuture).join();
            productDetailVO.setProduct(infoFuture.get());
        } catch (Exception e) {
            log.error("获取商品详情失败", e);
            Throwable cause = e.getCause();
            if (cause instanceof ServiceException serviceException) {
                throw serviceException;
            }
            throw new ServiceException("获取商品详情失败");
        }

        return Result.success(productDetailVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Long> addProduct(Product product) {
        if (product == null) {
            throw new ServiceException("商品参数不能为空");
        }
        if (product.getCreateTime() == null) {
            product.setCreateTime(new Date());
        }
        if (product.getStatus() == null) {
            product.setStatus(1);
        }
        if (product.getMainImage() == null || product.getMainImage().isBlank()) {
            product.setMainImage(DEFAULT_PRODUCT_IMAGE);
        }
        product.setUpdateTime(new Date());
        productMapper.insertProduct(product);
        Product savedProduct = productMapper.selectById(product.getId());
        evictProductCaches(product.getId());
        syncProductStockCache(savedProduct == null ? product : savedProduct);
        return Result.success(product.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> updateProduct(Product product) {
        if (product == null || product.getId() == null) {
            throw new ServiceException("更新商品时必须携带商品 ID");
        }
        product.setUpdateTime(new Date());
        int updateRows = productMapper.updateProduct(product);
        if (updateRows <= 0) {
            throw new ServiceException("商品更新失败");
        }
        evictProductCaches(product.getId());
        Product latestProduct = productMapper.selectById(product.getId());
        if (latestProduct != null) {
            syncProductStockCache(latestProduct);
        } else {
            clearProductDetailCache(product.getId());
        }
        return Result.success();
    }

    @Override
    public Result<Long> preheatProduct(Long productId) {
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new ServiceException("商品不存在或已下架");
        }
        long stock = Math.max(product.getStock() == null ? 0 : product.getStock(), 0);
        if (stock <= 0) {
            syncProductStockCache(product);
            throw new ServiceException("商品库存为 0，无法预热");
        }
        syncProductStockCache(product);
        cacheProductDetail(product);
        return Result.success(stock);
    }

    @Override
    public Result<List<ProductSkuDTO>> batchQueryProducts(List<Long> productIds) {
        if (CollUtil.isEmpty(productIds)) {
            return Result.success(Collections.emptyList());
        }
        List<Product> products = productMapper.selectByIds(productIds);
        List<ProductSkuDTO> result = products.stream()
                .map(product -> ProductSkuDTO.builder()
                        .id(product.getId())
                        .name(product.getName())
                        .mainImage(product.getMainImage())
                        .price(product.getPrice())
                        .status(product.getStatus() == null ? 1 : product.getStatus())
                        .stock(product.getStock())
                        .description(product.getDescription())
                        .sales(0L)
                        .createTime(product.getCreateTime())
                        .build())
                .collect(Collectors.toList());
        return Result.success(result);
    }

    @Override
    public Result<Page<ProductDoc>> searchProduct(String keyword, int page, int size) {
        int actualPage = Math.max(page, 1);
        int actualSize = Math.max(size, 1);
        ProductSearchRequest request = new ProductSearchRequest();
        request.setKeyword(keyword);
        request.setPage(actualPage);
        request.setSize(actualSize);
        request.setSortType("LATEST");
        ProductSearchResponseVO response = productSearchService.search(request);
        List<ProductDoc> records = response.getRecords().stream()
                .map(item -> ProductDoc.builder()
                        .id(item.getId())
                        .name(item.getName())
                        .mainImage(item.getMainImage())
                        .price(item.getPrice())
                        .stock(item.getStock())
                        .description(item.getDescription())
                        .sales(item.getSales())
                        .createTime(item.getCreateTime())
                        .status(item.getStatus())
                        .build())
                .toList();
        Page<ProductDoc> result = new PageImpl<>(records, PageRequest.of(actualPage - 1, actualSize), response.getTotal());
        return Result.success(result);
    }

    @Override
    public Result<ProductSearchResponseVO> searchProduct(ProductSearchRequest request) {
        return Result.success(productSearchService.search(request));
    }

    @Override
    public Result<List<ProductSearchItemVO>> listProducts(String keyword, Integer page, Integer size) {
        ProductSearchRequest request = new ProductSearchRequest();
        request.setKeyword(keyword);
        request.setPage(page == null ? 1 : page);
        request.setSize(size == null ? 20 : size);
        request.setSortType("LATEST");
        return Result.success(productSearchService.search(request).getRecords());
    }

    @SuppressWarnings("unchecked")
    private List<ProductDoc> loadRecommendProducts(Long categoryId, Long excludeId) {
        String cacheKey = PRODUCT_RECOMMEND.getValue() + categoryId + ":" + excludeId;
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached instanceof List<?> cachedList) {
            return (List<ProductDoc>) cachedList;
        }

        List<ProductDoc> docs = productMapper.selectRecommendProducts(categoryId, excludeId, 4);
        redisTemplate.opsForValue().set(cacheKey, docs, 10 + (long) (Math.random() * 5), TimeUnit.MINUTES);
        return docs;
    }

    private Product loadProductDetail(Long id) {
        String cacheKey = PRODUCT_DETAIL.getValue() + id;
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached instanceof Product cachedProduct) {
            if (cachedProduct.getId() == null) {
                throw new ServiceException("商品不存在或已下架");
            }
            return cachedProduct;
        }

        Product dbProduct = productMapper.selectById(id);
        if (dbProduct == null) {
            redisTemplate.opsForValue().set(cacheKey, new Product(), 1, TimeUnit.MINUTES);
            throw new ServiceException("商品不存在或已下架");
        }
        cacheProductDetail(dbProduct);
        syncProductStockCache(dbProduct);
        return dbProduct;
    }

    private void normalizeUploadRequest(AdminProductUploadRequest request) {
        if (request == null) {
            return;
        }
        if ((request.getName() == null || request.getName().isBlank()) && request.getTitle() != null) {
            request.setName(request.getTitle().trim());
        }
        if ((request.getMainImage() == null || request.getMainImage().isBlank()) && request.getImageUrl() != null) {
            request.setMainImage(request.getImageUrl().trim());
        }
        if (request.getMainImage() == null || request.getMainImage().isBlank()) {
            request.setMainImage(DEFAULT_PRODUCT_IMAGE);
        }
        if (request.getStock() == null && request.getInventory() != null) {
            request.setStock(request.getInventory());
        }
        if (request.getStatus() == null) {
            request.setStatus(1);
        }
    }

    private void validateUploadRequest(AdminProductUploadRequest request) {
        if (request == null) {
            throw new ServiceException("商品上传参数不能为空");
        }
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            throw new ServiceException("商品名称不能为空");
        }
        if (request.getCategoryId() == null) {
            throw new ServiceException("商品分类不能为空");
        }
        if (request.getPrice() == null || request.getPrice() <= 0) {
            throw new ServiceException("商品价格必须大于 0");
        }
        if (request.getStock() == null || request.getStock() < 0) {
            throw new ServiceException("商品库存不能小于 0");
        }
        if (request.getStartTime() == null || request.getEndTime() == null) {
            throw new ServiceException("请完整设置活动开始时间和结束时间");
        }
        if (!request.getEndTime().after(request.getStartTime())) {
            throw new ServiceException("活动结束时间必须晚于开始时间");
        }
    }

    private AdminProductUploadResponse buildUploadResponse(Product product) {
        return AdminProductUploadResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .mainImage(product.getMainImage())
                .price(product.getPrice())
                .stock(product.getStock())
                .status(product.getStatus() == null ? 1 : product.getStatus())
                .build();
    }

    private void syncProductStockCache(Product product) {
        if (product == null || product.getId() == null) {
            return;
        }
        long stock = Math.max(product.getStock() == null ? 0 : product.getStock(), 0);
        boolean online = product.getStatus() == null || product.getStatus() == 1;
        long availableStock = online ? stock : 0;
        String preheatKey = PRODUCT_PREHEAT_HASH.getValue() + product.getId();
        redisTemplate.opsForHash().put(preheatKey, "productId", String.valueOf(product.getId()));
        redisTemplate.opsForHash().put(preheatKey, "stock", String.valueOf(availableStock));
        redisTemplate.opsForHash().put(preheatKey, "exists", availableStock > 0 ? "1" : "0");
        redisTemplate.opsForHash().put(preheatKey, "status", String.valueOf(product.getStatus() == null ? 1 : product.getStatus()));
        redisTemplate.expire(preheatKey, 1, TimeUnit.DAYS);
        redisTemplate.opsForValue().set(GOODS_PRE_DEDUCTION.getValue() + product.getId(), availableStock, 1, TimeUnit.DAYS);
        redisTemplate.opsForValue().set(GOODS_IS_EXIST.getValue() + product.getId(), availableStock > 0 ? 1 : 0, 1, TimeUnit.DAYS);
        if (online) {
            cacheProductDetail(product);
        } else {
            clearProductDetailCache(product.getId());
        }
    }

    private void cacheProductDetail(Product product) {
        redisTemplate.opsForValue().set(
                PRODUCT_DETAIL.getValue() + product.getId(),
                product,
                PRODUCT_DETAIL_TTL_MINUTES + (long) (Math.random() * 10),
                TimeUnit.MINUTES
        );
    }

    private void evictProductCaches(Long productId) {
        if (productId != null) {
            clearProductDetailCache(productId);
        }
        clearByPrefix(PRODUCT_SEARCH.getValue());
        clearByPrefix(PRODUCT_RECOMMEND.getValue());
    }

    private void clearProductDetailCache(Long productId) {
        redisTemplate.delete(PRODUCT_DETAIL.getValue() + productId);
    }

    private void clearByPrefix(String prefix) {
        try {
            var keys = redisTemplate.keys(prefix + "*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception e) {
            log.warn("清理 Redis 缓存失败, prefix={}", prefix, e);
        }
    }

    private Path resolveUploadDir() {
        return Path.of(System.getProperty("user.dir"), "uploads", "products");
    }

    private String extractExtension(String originalFilename) {
        if (originalFilename == null || !originalFilename.contains(".")) {
            return ".png";
        }
        return originalFilename.substring(originalFilename.lastIndexOf('.'));
    }
}
