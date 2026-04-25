package com.suddenfix.order.controller;

import com.suddenfix.common.result.Result;
import com.suddenfix.order.domain.dto.OrderDTO;
import com.suddenfix.order.domain.vo.OrderViewVO;
import com.suddenfix.order.service.IOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/order")
@RequiredArgsConstructor
public class OrderController {

    private final IOrderService orderService;

    @PostMapping("/buy")
    public Result<Long> createOrder(@RequestBody OrderDTO orderDTO, @RequestHeader("user_id") Long userId) {
        orderDTO.setUserId(userId);
        return orderService.createOrder(orderDTO);
    }

    @GetMapping("/status/{orderId}")
    public Result<Integer> getUserOrderStatus(@RequestHeader("user_id") Long userId,
                                              @PathVariable Long orderId) {
        return orderService.getUserOrderStatus(userId, orderId);
    }

    @GetMapping("/list")
    public Result<List<OrderViewVO>> listUserOrders(@RequestHeader("user_id") Long userId) {
        return orderService.listUserOrders(userId);
    }

    @GetMapping("/{orderId}")
    public Result<OrderViewVO> getUserOrderDetail(@RequestHeader("user_id") Long userId,
                                                  @PathVariable Long orderId) {
        return orderService.getUserOrderDetail(userId, orderId);
    }

    @PostMapping("/{orderId}/cancel")
    public Result<Void> cancelUserOrder(@RequestHeader("user_id") Long userId,
                                        @PathVariable Long orderId) {
        return orderService.cancelUserOrder(userId, orderId);
    }

    @PostMapping("/{orderId}/refund")
    public Result<Void> refundUserOrder(@RequestHeader("user_id") Long userId,
                                        @PathVariable Long orderId) {
        return orderService.refundUserOrder(userId, orderId);
    }

}
