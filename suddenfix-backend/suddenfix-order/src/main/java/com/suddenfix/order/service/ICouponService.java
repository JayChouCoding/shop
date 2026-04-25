package com.suddenfix.order.service;

import com.suddenfix.common.result.Result;
import com.suddenfix.order.domain.dto.CouponPreheatDTO;
import com.suddenfix.order.domain.pojo.Coupon;
import com.suddenfix.order.domain.vo.CouponPreheatVO;

public interface ICouponService {
    Result<Void> preheatCoupon(CouponPreheatDTO couponPreheatDTO);

    Result<CouponPreheatVO> getCoupon(Long couponId,Long userId);

    Result<CouponPreheatDTO> insertCoupon(Coupon coupon);
}
