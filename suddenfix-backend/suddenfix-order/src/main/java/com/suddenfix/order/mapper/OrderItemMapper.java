package com.suddenfix.order.mapper;

import com.suddenfix.order.domain.pojo.OrderItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface OrderItemMapper {
    int insertOrderItemBatch(@Param("orderItemList") List<OrderItem> orderItemList);

    List<OrderItem> selectByOrderId(@Param("orderId") Long orderId);

    List<OrderItem> selectByOrderIdAndUserId(@Param("orderId") Long orderId, @Param("userId") Long userId);
}
