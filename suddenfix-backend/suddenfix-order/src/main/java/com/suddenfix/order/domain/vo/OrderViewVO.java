package com.suddenfix.order.domain.vo;

import com.suddenfix.order.domain.pojo.Order;
import com.suddenfix.order.domain.pojo.OrderItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderViewVO {

    private Order order;

    private List<OrderItem> items;

    private OrderCouponVO coupon;
}
