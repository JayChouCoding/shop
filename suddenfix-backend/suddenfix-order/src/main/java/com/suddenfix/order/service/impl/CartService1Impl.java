package com.suddenfix.order.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.suddenfix.common.dto.ProductSkuDTO;
import com.suddenfix.common.exception.ServiceException;
import com.suddenfix.common.result.Result;
import com.suddenfix.order.domain.dto.CartAddRequest;
import com.suddenfix.order.domain.dto.CartUpdateRequest;
import com.suddenfix.order.domain.vo.CartDetailVO;
import com.suddenfix.order.domain.vo.CartItemVO;
import com.suddenfix.order.feign.ProductFeign;
import com.suddenfix.order.service.CartService1;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.suddenfix.common.enums.RedisPreMessage.CART;
import static com.suddenfix.common.enums.RedisPreMessage.CART_SELECTED;

@Service
@RequiredArgsConstructor
public class CartService1Impl implements CartService1 {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ProductFeign productFeign;

    @Override
    public Result<Void> addItem(Long userId, CartAddRequest request) {
        if (request.getProductId() == null || request.getQuantity() == null || request.getQuantity() <= 0) {
            throw new ServiceException("加入购物车数量必须大于 0");
        }
        String cartKey = cartKey(userId);
        // 数量直接走 HINCRBY，避免先查后改导致并发覆盖。
        redisTemplate.opsForHash().increment(cartKey, String.valueOf(request.getProductId()), request.getQuantity());
        // 默认勾选，结算时只需要从 selected 集合取交集即可。
        redisTemplate.opsForSet().add(selectedKey(userId), String.valueOf(request.getProductId()));
        return Result.success();
    }

    @Override
    public Result<Void> updateQuantity(Long userId, CartUpdateRequest request) {
        if (request.getProductId() == null || request.getQuantity() == null) {
            throw new ServiceException("商品和数量不能为空");
        }
        String cartKey = cartKey(userId);
        String field = String.valueOf(request.getProductId());
        if (request.getQuantity() <= 0) {
            redisTemplate.opsForHash().delete(cartKey, field);
            redisTemplate.opsForSet().remove(selectedKey(userId), field);
            return Result.success();
        }
        redisTemplate.opsForHash().put(cartKey, field, request.getQuantity());
        return Result.success();
    }

    @Override
    public Result<Void> switchItemSelected(Long userId, Long productId, Boolean selected) {
        if (productId == null) {
            throw new ServiceException("商品不能为空");
        }
        String cartKey = cartKey(userId);
        String field = String.valueOf(productId);
        if (!Boolean.TRUE.equals(redisTemplate.opsForHash().hasKey(cartKey, field))) {
            throw new ServiceException("购物车商品不存在");
        }
        String selectedRedisKey = selectedKey(userId);
        if (Boolean.TRUE.equals(selected)) {
            redisTemplate.opsForSet().add(selectedRedisKey, field);
        } else {
            redisTemplate.opsForSet().remove(selectedRedisKey, field);
        }
        return Result.success();
    }

    @Override
    public Result<Void> switchAllSelected(Long userId, Boolean selected) {
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(cartKey(userId));
        if (entries.isEmpty()) {
            return Result.success();
        }
        List<String> productIds = entries.keySet().stream().map(String::valueOf).toList();
        String selectedRedisKey = selectedKey(userId);
        if (Boolean.TRUE.equals(selected)) {
            redisTemplate.opsForSet().add(selectedRedisKey, productIds.toArray());
        } else {
            redisTemplate.delete(selectedRedisKey);
        }
        return Result.success();
    }

    @Override
    public Result<Void> clearCheckedItems(Long userId, List<Long> productIds) {
        if (CollUtil.isEmpty(productIds)) {
            return Result.success();
        }
        List<String> fields = productIds.stream().map(String::valueOf).toList();
        String cartKey = cartKey(userId);
        redisTemplate.opsForHash().delete(cartKey, fields.toArray());
        redisTemplate.opsForSet().remove(selectedKey(userId), fields.toArray());
        return Result.success();
    }

    @Override
    public Result<CartDetailVO> getCartDetail(Long userId) {
        Map<Object, Object> cartEntries = redisTemplate.opsForHash().entries(cartKey(userId));
        if (cartEntries.isEmpty()) {
            return Result.success(CartDetailVO.builder()
                    .items(Collections.emptyList())
                    .totalKinds(0)
                    .selectedKinds(0)
                    .totalAmount(0L)
                    .selectedAmount(0L)
                    .build());
        }

        List<Long> productIds = cartEntries.keySet().stream()
                .map(field -> Convert.toLong(field.toString()))
                .toList();
        Result<List<ProductSkuDTO>> productResult = productFeign.getProductsByIds(productIds);
        List<ProductSkuDTO> productList = productResult == null
                ? Collections.emptyList()
                : ObjectUtil.defaultIfNull(productResult.getData(), Collections.emptyList());
        Map<Long, ProductSkuDTO> productMap = productList.stream()
                .collect(Collectors.toMap(ProductSkuDTO::getId, Function.identity(), (left, right) -> left));

        Set<Object> selectedProducts = redisTemplate.opsForSet().members(selectedKey(userId));
        Set<String> selectedSet = selectedProducts == null ? Collections.emptySet() :
                selectedProducts.stream().map(String::valueOf).collect(Collectors.toSet());

        List<CartItemVO> items = new ArrayList<>();
        long totalAmount = 0L;
        long selectedAmount = 0L;
        int selectedKinds = 0;

        for (Map.Entry<Object, Object> entry : cartEntries.entrySet()) {
            Long productId = Convert.toLong(entry.getKey().toString());
            Integer quantity = Convert.toInt(entry.getValue());
            ProductSkuDTO sku = productMap.get(productId);
            boolean selected = selectedSet.contains(String.valueOf(productId));
            boolean available = sku != null && Integer.valueOf(1).equals(sku.getStatus());
            long unitPrice = available ? ObjectUtil.defaultIfNull(sku.getPrice(), 0L) : 0L;
            long subtotal = unitPrice * quantity;

            // 金额汇总永远基于商品中心返回的最新快照，避免购物车价格脏读。
            totalAmount += subtotal;
            if (selected && available) {
                selectedAmount += subtotal;
                selectedKinds++;
            }

            items.add(CartItemVO.builder()
                    .productId(productId)
                    .productName(sku == null ? "商品不存在" : sku.getName())
                    .mainImage(sku == null ? StrUtil.EMPTY : sku.getMainImage())
                    .price(unitPrice)
                    .quantity(quantity)
                    .selected(selected)
                    .available(available)
                    .subtotalAmount(subtotal)
                    .unavailableReason(available ? null : "商品已下架或不存在")
                    .build());
        }

        return Result.success(CartDetailVO.builder()
                .items(items)
                .totalKinds(items.size())
                .selectedKinds(selectedKinds)
                .totalAmount(totalAmount)
                .selectedAmount(selectedAmount)
                .build());
    }

    private String cartKey(Long userId) {
        return CART.getValue() + userId;
    }

    private String selectedKey(Long userId) {
        return CART_SELECTED.getValue() + userId;
    }
}
