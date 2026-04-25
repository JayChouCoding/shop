package com.suddenfix.order.service;

import com.suddenfix.common.result.Result;
import com.suddenfix.order.domain.dto.CartAddRequest;
import com.suddenfix.order.domain.dto.CartUpdateRequest;
import com.suddenfix.order.domain.vo.CartDetailVO;

import java.util.List;

public interface CartService1 {

    Result<Void> addItem(Long userId, CartAddRequest request);

    Result<Void> updateQuantity(Long userId, CartUpdateRequest request);

    Result<Void> switchItemSelected(Long userId, Long productId, Boolean selected);

    Result<Void> switchAllSelected(Long userId, Boolean selected);

    Result<Void> clearCheckedItems(Long userId, List<Long> productIds);

    Result<CartDetailVO> getCartDetail(Long userId);
}
