package com.suddenfix.order.controller;
import com.suddenfix.common.result.Result;
import com.suddenfix.order.domain.dto.CouponPreheatDTO;
import com.suddenfix.order.domain.pojo.Coupon;
import com.suddenfix.order.domain.vo.CouponPreheatVO;
import com.suddenfix.order.service.ICouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

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

    @PostMapping("/insertCoupon")
    public Result<CouponPreheatDTO> insertCoupon(@RequestBody Coupon coupon){
        return couponService.insertCoupon(coupon);
    }
}
