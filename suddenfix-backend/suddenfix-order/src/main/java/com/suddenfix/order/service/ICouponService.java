package com.suddenfix.order.service;

import com.suddenfix.common.result.Result;
import com.suddenfix.order.domain.dto.CouponPreheatDTO;
import com.suddenfix.order.domain.pojo.Coupon;
import com.suddenfix.order.domain.vo.CouponActivityVO;
import com.suddenfix.order.domain.vo.CouponPreheatVO;
import com.suddenfix.order.domain.vo.UserCouponVO;

import java.util.List;

public interface ICouponService {
    Result<Void> preheatCoupon(CouponPreheatDTO couponPreheatDTO);

    Result<CouponPreheatVO> getCoupon(Long couponId, Long userId);

    Result<List<CouponActivityVO>> listAvailableCoupons(Long userId);

    Result<List<UserCouponVO>> listUserCoupons(Long userId);

    Result<List<UserCouponVO>> listCheckoutCoupons(Long userId, Long orderAmount);

    Result<CouponPreheatDTO> insertCoupon(Coupon coupon);
}
