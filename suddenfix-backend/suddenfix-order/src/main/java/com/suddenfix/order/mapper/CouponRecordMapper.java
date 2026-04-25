package com.suddenfix.order.mapper;

import com.suddenfix.order.domain.pojo.CouponRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CouponRecordMapper {
    void insertCouponRecord(CouponRecord couponRecord);

    int bindOrderToCoupon(@Param("userId") Long userId,
                          @Param("couponToken") String couponToken,
                          @Param("orderId") Long orderId);

    int markCouponUsedByOrderId(@Param("orderId") Long orderId);

    int rollbackCouponUsedByOrderId(@Param("orderId") Long orderId);

    int clearCouponBindingByOrderId(@Param("orderId") Long orderId);

    CouponRecord selectByOrderId(@Param("orderId") Long orderId);
}
