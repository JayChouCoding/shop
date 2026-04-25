package com.suddenfix.shipping.controller;

import com.suddenfix.common.exception.ServiceException;
import com.suddenfix.common.result.Result;
import com.suddenfix.shipping.domain.dto.ShipOrderRequest;
import com.suddenfix.shipping.domain.pojo.ShippingOrder;
import com.suddenfix.shipping.domain.pojo.ShippingRecord;
import com.suddenfix.shipping.service.IShippingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin/shipping")
@RequiredArgsConstructor
public class AdminShippingController {

    private final IShippingService shippingService;

    @GetMapping("/pending")
    public Result<List<ShippingOrder>> listPendingOrders(@RequestParam(value = "limit", required = false) Integer limit,
                                                         @RequestHeader(value = "role", required = false) Integer role) {
        validateAdminRole(role);
        return shippingService.listPendingDeliveryOrders(limit);
    }

    @PostMapping("/deliver")
    public Result<ShippingRecord> deliverOrder(@RequestBody ShipOrderRequest request,
                                               @RequestHeader(value = "role", required = false) Integer role) {
        validateAdminRole(role);
        return shippingService.shipOrder(request);
    }

    private void validateAdminRole(Integer role) {
        if (role == null || role != 1) {
            throw new ServiceException("仅商家账号可使用该接口");
        }
    }
}
