package com.suddenfix.order.mapper;

import com.suddenfix.order.domain.pojo.Coupon;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CouponMapper {
    int insertCoupon(Coupon coupon);

    Coupon selectCoupon(Long couponId);
}
