package com.suddenfix.order.controller;
import com.suddenfix.common.result.Result;
import com.suddenfix.order.domain.dto.CouponPreheatDTO;
import com.suddenfix.order.domain.pojo.Coupon;
import com.suddenfix.order.domain.vo.CouponActivityVO;
import com.suddenfix.order.domain.vo.CouponPreheatVO;
import com.suddenfix.order.domain.vo.UserCouponVO;
import com.suddenfix.order.service.ICouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping({"/coupon", "/order/coupon"})
@RequiredArgsConstructor
public class CouponController {

    private final ICouponService couponService;


    @PostMapping("/preheat")
    public Result<Void> preheatCoupon(@RequestBody CouponPreheatDTO couponPreheatDTO){
        return couponService.preheatCoupon(couponPreheatDTO);
    }

    @PostMapping("/getCoupon/{couponId}")
    public Result<CouponPreheatVO> getCoupon(@PathVariable Long couponId, @RequestHeader("user_id") Long userId){
        return couponService.getCoupon(couponId,userId);
    }

    @GetMapping("/available")
    public Result<List<CouponActivityVO>> listAvailableCoupons(@RequestHeader("user_id") Long userId) {
        return couponService.listAvailableCoupons(userId);
    }

    @GetMapping("/my")
    public Result<List<UserCouponVO>> listUserCoupons(@RequestHeader("user_id") Long userId) {
        return couponService.listUserCoupons(userId);
    }

    @GetMapping("/checkout/available")
    public Result<List<UserCouponVO>> listCheckoutCoupons(@RequestHeader("user_id") Long userId,
                                                          @RequestParam("orderAmount") Long orderAmount) {
        return couponService.listCheckoutCoupons(userId, orderAmount);
    }

    @PostMapping("/insertCoupon")
    public Result<CouponPreheatDTO> insertCoupon(@RequestBody Coupon coupon){
        return couponService.insertCoupon(coupon);
    }
}
