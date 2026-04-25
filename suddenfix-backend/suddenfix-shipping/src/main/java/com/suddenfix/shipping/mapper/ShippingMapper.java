package com.suddenfix.shipping.mapper;

import com.suddenfix.shipping.domain.pojo.ShippingRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ShippingMapper {
    ShippingRecord selectByOrderId(@Param("orderId") Long orderId);
    int insert(ShippingRecord shippingRecord);
    int updateToShipped(ShippingRecord shippingRecord);
    int updateToCompleted(@Param("orderId") Long orderId);
}
