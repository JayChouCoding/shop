package com.suddenfix.shipping.service;

import com.suddenfix.common.result.Result;
import com.suddenfix.shipping.domain.dto.CompleteShippingRequest;
import com.suddenfix.shipping.domain.dto.ShipOrderRequest;
import com.suddenfix.shipping.domain.pojo.ShippingOrder;
import com.suddenfix.shipping.domain.pojo.ShippingRecord;

import java.util.List;

public interface IShippingService {
    Result<ShippingRecord> shipOrder(ShipOrderRequest request);

    Result<ShippingRecord> completeOrder(CompleteShippingRequest request);

    Result<ShippingRecord> getShippingDetail(Long orderId);

    Result<List<ShippingOrder>> listPendingDeliveryOrders(Integer limit);

    void initPendingShipping(ShippingRecord shippingRecord);
}
