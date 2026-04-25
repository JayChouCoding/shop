package com.suddenfix.product.service.impl;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.suddenfix.product.domain.dto.ProductSearchRequest;
import com.suddenfix.product.domain.pojo.ProductDoc;
import com.suddenfix.product.domain.vo.ProductSearchItemVO;
import com.suddenfix.product.domain.vo.ProductSearchResponseVO;
import com.suddenfix.product.mapper.ProductMapper;
import com.suddenfix.product.service.ProductSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.suddenfix.common.enums.RedisPreMessage.PRODUCT_SEARCH;

@Service
@RequiredArgsConstructor
public class ProductSearchServiceImpl implements ProductSearchService {

    private final ProductMapper productMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public ProductSearchResponseVO search(ProductSearchRequest request) {
        int page = ObjectUtil.defaultIfNull(request.getPage(), 1);
        int size = ObjectUtil.defaultIfNull(request.getSize(), 10);
        page = Math.max(page, 1);
        size = Math.max(size, 1);
        int offset = (page - 1) * size;
        boolean latestFirst = "LATEST".equalsIgnoreCase(request.getSortType());

        String cacheKey = buildSearchCacheKey(request, page, size, latestFirst);
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached instanceof ProductSearchResponseVO cachedResponse) {
            return cachedResponse;
        }

        List<ProductDoc> docs = productMapper.searchProducts(
                request.getKeyword(),
                request.getMinPrice(),
                request.getMaxPrice(),
                offset,
                size,
                latestFirst
        );
        long total = ObjectUtil.defaultIfNull(
                productMapper.countSearchProducts(request.getKeyword(), request.getMinPrice(), request.getMaxPrice()),
                0L
        );
        List<ProductSearchItemVO> records = docs.stream()
                .map(doc -> ProductSearchItemVO.builder()
                        .id(doc.getId())
                        .name(highlightKeyword(doc.getName(), request.getKeyword()))
                        .description(highlightKeyword(doc.getDescription(), request.getKeyword()))
                        .mainImage(doc.getMainImage())
                        .price(doc.getPrice())
                        .stock(ObjectUtil.defaultIfNull(doc.getStock(), 0))
                        .status(ObjectUtil.defaultIfNull(doc.getStatus(), 1))
                        .sales(ObjectUtil.defaultIfNull(doc.getSales(), 0L))
                        .createTime(doc.getCreateTime())
                        .build())
                .toList();

        ProductSearchResponseVO response = ProductSearchResponseVO.builder()
                .total(total)
                .page(page)
                .size(size)
                .records(records)
                .build();
        long ttl = total > 0 ? 5 + (long) (Math.random() * 3) : 1;
        redisTemplate.opsForValue().set(cacheKey, response, ttl, TimeUnit.MINUTES);
        return response;
    }

    private String buildSearchCacheKey(ProductSearchRequest request, int page, int size, boolean latestFirst) {
        String keyword = StrUtil.blankToDefault(request.getKeyword(), "_");
        String minPrice = request.getMinPrice() == null ? "_" : String.valueOf(request.getMinPrice());
        String maxPrice = request.getMaxPrice() == null ? "_" : String.valueOf(request.getMaxPrice());
        return PRODUCT_SEARCH.getValue() + keyword + ":" + minPrice + ":" + maxPrice + ":" + page + ":" + size + ":" + latestFirst;
    }

    private String highlightKeyword(String content, String keyword) {
        if (StrUtil.isBlank(content) || StrUtil.isBlank(keyword)) {
            return content;
        }
        if (!content.contains(keyword)) {
            return content;
        }
        return content.replace(keyword, "<em>" + keyword + "</em>");
    }
}
