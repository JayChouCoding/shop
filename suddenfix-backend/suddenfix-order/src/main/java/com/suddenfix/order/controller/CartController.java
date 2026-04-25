package com.suddenfix.order.controller;

import com.suddenfix.common.result.Result;
import com.suddenfix.order.domain.dto.CartAddRequest;
import com.suddenfix.order.domain.dto.CartCheckoutClearRequest;
import com.suddenfix.order.domain.dto.CartItemSelectRequest;
import com.suddenfix.order.domain.dto.CartSelectAllRequest;
import com.suddenfix.order.domain.dto.CartUpdateRequest;
import com.suddenfix.order.domain.vo.CartDetailVO;
import com.suddenfix.order.service.CartService1;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/cart", "/order/cart"})
@RequiredArgsConstructor
public class CartController {

    private final CartService1 cartService1;

    @PostMapping("/item")
    public Result<Void> addItem(@RequestHeader("user_id") Long userId, @RequestBody CartAddRequest request) {
        return cartService1.addItem(userId, request);
    }

    @PutMapping("/item")
    public Result<Void> updateItem(@RequestHeader("user_id") Long userId, @RequestBody CartUpdateRequest request) {
        return cartService1.updateQuantity(userId, request);
    }

    @PutMapping("/item/selected")
    public Result<Void> updateItemSelected(@RequestHeader("user_id") Long userId,
                                           @RequestBody CartItemSelectRequest request) {
        return cartService1.switchItemSelected(userId, request.getProductId(), request.getSelected());
    }

    @PutMapping("/select-all")
    public Result<Void> selectAll(@RequestHeader("user_id") Long userId, @RequestBody CartSelectAllRequest request) {
        return cartService1.switchAllSelected(userId, request.getSelected());
    }

    @DeleteMapping("/checked")
    public Result<Void> clearChecked(@RequestHeader("user_id") Long userId,
                                     @RequestBody CartCheckoutClearRequest request) {
        return cartService1.clearCheckedItems(userId, request.getProductIds());
    }

    @GetMapping
    public Result<CartDetailVO> getCart(@RequestHeader("user_id") Long userId) {
        return cartService1.getCartDetail(userId);
    }
}
