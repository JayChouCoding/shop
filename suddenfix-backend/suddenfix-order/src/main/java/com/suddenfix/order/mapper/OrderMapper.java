package com.suddenfix.order.mapper;

import com.suddenfix.order.domain.pojo.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface OrderMapper {
    int insertOrder(Order order);

    int updateOrderStatusToPendingPayment(@Param("orderId") Long orderId);

    int updateOrderStatusToPaid(@Param("orderId") Long orderId,@Param("outTradeNo") String outTradeNo);

    int desensitizeOrderUserInfo(@Param("userId") Long userId);

    Integer selectStatusByOrderIdAndUserId(@Param("orderId") Long orderId, @Param("userId") Long userId);

    int cancelOrder(@Param("userId") Long userId, @Param("orderId") Long orderId);

    int closePaidOrder(@Param("userId") Long userId, @Param("orderId") Long orderId);

    Order selectByOrderId(@Param("orderId") Long orderId);

    List<Order> selectByUserId(@Param("userId") Long userId);
}
