package com.suddenfix.shipping.controller;

import com.suddenfix.common.result.Result;
import com.suddenfix.shipping.domain.dto.CompleteShippingRequest;
import com.suddenfix.shipping.domain.dto.ShipOrderRequest;
import com.suddenfix.shipping.domain.pojo.ShippingRecord;
import com.suddenfix.shipping.service.IShippingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/shipping")
@RequiredArgsConstructor
public class ShippingController {

    private final IShippingService shippingService;

    @PostMapping("/admin/ship")
    public Result<ShippingRecord> shipOrder(@RequestBody ShipOrderRequest request) {
        return shippingService.shipOrder(request);
    }

    @PostMapping("/admin/complete")
    public Result<ShippingRecord> completeOrder(@RequestBody CompleteShippingRequest request) {
        return shippingService.completeOrder(request);
    }

    @GetMapping("/order/{orderId}")
    public Result<ShippingRecord> getShipping(@PathVariable Long orderId) {
        return shippingService.getShippingDetail(orderId);
    }
}
