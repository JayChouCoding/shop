package com.suddenfix.order.service;

import com.suddenfix.common.result.Result;
import com.suddenfix.order.domain.dto.OrderDTO;
import com.suddenfix.order.domain.vo.OrderViewVO;

import java.util.List;

public interface IOrderService {
    Result<Long> createOrder(OrderDTO orderDTO);

    Result<Integer> getUserOrderStatus(Long userId, Long orderId);

    Result<List<OrderViewVO>> listUserOrders(Long userId);

    Result<OrderViewVO> getUserOrderDetail(Long userId, Long orderId);
}
