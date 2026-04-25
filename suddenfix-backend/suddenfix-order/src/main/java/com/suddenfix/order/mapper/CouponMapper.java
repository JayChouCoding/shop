package com.suddenfix.order.mapper;

import com.suddenfix.order.domain.pojo.Coupon;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

@Mapper
public interface CouponMapper {
    int insertCoupon(Coupon coupon);

    Coupon selectCoupon(Long couponId);

    List<Coupon> selectActiveCoupons(@Param("now") Date now);
}
