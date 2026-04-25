package com.suddenfix.shipping.mapper;

import com.suddenfix.shipping.domain.pojo.ShippingOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ShippingOrderMapper {
    ShippingOrder selectByOrderId(@Param("orderId") Long orderId);

    List<ShippingOrder> selectPendingDeliveryOrders(@Param("limit") Integer limit);

    int updateToShipped(@Param("orderId") Long orderId);

    int updateToCompleted(@Param("orderId") Long orderId);
}
