package com.suddenfix.order.controller;

import com.suddenfix.common.exception.ServiceException;
import com.suddenfix.common.result.Result;
import com.suddenfix.order.domain.dto.CouponPreheatDTO;
import com.suddenfix.order.service.ICouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/coupon")
@RequiredArgsConstructor
public class AdminCouponController {

    private final ICouponService couponService;

    @PostMapping("/preheat")
    public Result<Void> preheatCoupon(@RequestBody CouponPreheatDTO couponPreheatDTO,
                                      @RequestHeader(value = "role", required = false) Integer role) {
        validateAdminRole(role);
        return couponService.preheatCoupon(couponPreheatDTO);
    }

    private void validateAdminRole(Integer role) {
        if (role == null || role != 1) {
            throw new ServiceException("仅商家账号可使用该接口");
        }
    }
}
