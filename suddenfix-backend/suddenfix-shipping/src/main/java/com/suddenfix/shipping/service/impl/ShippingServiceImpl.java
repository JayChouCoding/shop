package com.suddenfix.shipping.service.impl;

import cn.hutool.core.util.IdUtil;
import com.suddenfix.common.enums.OrderStatic;
import com.suddenfix.common.exception.ServiceException;
import com.suddenfix.common.result.Result;
import com.suddenfix.shipping.domain.dto.CompleteShippingRequest;
import com.suddenfix.shipping.domain.dto.ShipOrderRequest;
import com.suddenfix.shipping.domain.pojo.ShippingOrder;
import com.suddenfix.shipping.domain.pojo.ShippingRecord;
import com.suddenfix.shipping.mapper.ShippingMapper;
import com.suddenfix.shipping.mapper.ShippingOrderMapper;
import com.suddenfix.shipping.service.IShippingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ShippingServiceImpl implements IShippingService {

    private static final String DEFAULT_EXPRESS_COMPANY = "SUDDENFIX-EXPRESS";
    private static final String PENDING_PACKING_COMPANY = "等待商家打包";
    private static final String PENDING_PACKING_REMARK = "订单已支付成功，物流单正在生成中，请稍后查看。";

    private final ShippingMapper shippingMapper;
    private final ShippingOrderMapper shippingOrderMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<ShippingRecord> shipOrder(ShipOrderRequest request) {
        if (request == null || request.getOrderId() == null) {
            throw new ServiceException("订单号不能为空");
        }

        ShippingOrder order = shippingOrderMapper.selectByOrderId(request.getOrderId());
        if (order == null) {
            throw new ServiceException("订单不存在");
        }

        ShippingRecord existed = shippingMapper.selectByOrderId(request.getOrderId());
        if (OrderStatic.SHIPPED.getCode().equals(order.getStatus())) {
            if (existed == null) {
                throw new ServiceException("订单已发货，但物流单不存在，请检查数据");
            }
            return Result.success(existed);
        }
        if (!OrderStatic.PAID.getCode().equals(order.getStatus())) {
            throw new ServiceException("只有已支付订单才能发货");
        }

        String logisticsNo = resolveLogisticsNo(request.getLogisticsNo());
        String expressCompany = request.getExpressCompany() == null || request.getExpressCompany().isBlank()
                ? DEFAULT_EXPRESS_COMPANY
                : request.getExpressCompany().trim();

        if (existed != null) {
            if (existed.getShippingStatus() != null && existed.getShippingStatus() >= 1) {
                return Result.success(existed);
            }
            existed.setLogisticsNo(logisticsNo);
            existed.setExpressCompany(expressCompany);
            existed.setRemark(request.getRemark());
            shippingMapper.updateToShipped(existed);
            shippingOrderMapper.updateToShipped(request.getOrderId());
            return Result.success(shippingMapper.selectByOrderId(request.getOrderId()));
        }

        ShippingRecord shippingRecord = ShippingRecord.builder()
                .shippingId(IdUtil.getSnowflakeNextId())
                .orderId(order.getOrderId())
                .userId(request.getUserId() == null ? order.getUserId() : request.getUserId())
                .logisticsNo(logisticsNo)
                .expressCompany(expressCompany)
                .shippingStatus(1)
                .receiverName(order.getReceiverName())
                .receiverPhone(order.getReceiverPhone())
                .receiverAddress(order.getReceiverAddress())
                .shipTime(new Date())
                .remark(request.getRemark())
                .build();

        shippingMapper.insert(shippingRecord);
        shippingOrderMapper.updateToShipped(request.getOrderId());
        return Result.success(shippingMapper.selectByOrderId(request.getOrderId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<ShippingRecord> completeOrder(CompleteShippingRequest request) {
        ShippingRecord shippingRecord = shippingMapper.selectByOrderId(request.getOrderId());
        if (shippingRecord == null) {
            throw new ServiceException("物流记录不存在");
        }
        if (shippingRecord.getShippingStatus() != null && shippingRecord.getShippingStatus() >= 3) {
            return Result.success(shippingRecord);
        }
        shippingMapper.updateToCompleted(request.getOrderId());
        shippingOrderMapper.updateToCompleted(request.getOrderId());
        return Result.success(shippingMapper.selectByOrderId(request.getOrderId()));
    }

    @Override
    public Result<ShippingRecord> getShippingDetail(Long orderId) {
        ShippingRecord shippingRecord = shippingMapper.selectByOrderId(orderId);
        if (shippingRecord != null) {
            return Result.success(shippingRecord);
        }

        ShippingOrder order = shippingOrderMapper.selectByOrderId(orderId);
        if (order == null) {
            return Result.success(buildPendingShippingRecord(orderId, null, null, null, null));
        }
        return Result.success(buildPendingShippingRecord(
                orderId,
                order.getUserId(),
                order.getReceiverName(),
                order.getReceiverPhone(),
                order.getReceiverAddress()
        ));
    }

    @Override
    public Result<List<ShippingOrder>> listPendingDeliveryOrders(Integer limit) {
        int actualLimit = limit == null || limit <= 0 ? 20 : Math.min(limit, 100);
        return Result.success(shippingOrderMapper.selectPendingDeliveryOrders(actualLimit));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void initPendingShipping(ShippingRecord shippingRecord) {
        if (shippingRecord == null || shippingRecord.getOrderId() == null || shippingRecord.getUserId() == null) {
            throw new ServiceException("初始化物流单缺少必要参数");
        }
        ShippingRecord existed = shippingMapper.selectByOrderId(shippingRecord.getOrderId());
        if (existed != null) {
            return;
        }
        shippingMapper.insert(shippingRecord);
    }

    private ShippingRecord buildPendingShippingRecord(Long orderId,
                                                      Long userId,
                                                      String receiverName,
                                                      String receiverPhone,
                                                      String receiverAddress) {
        return ShippingRecord.builder()
                .shippingId(null)
                .orderId(orderId)
                .userId(userId)
                .logisticsNo(null)
                .expressCompany(PENDING_PACKING_COMPANY)
                .shippingStatus(0)
                .receiverName(receiverName)
                .receiverPhone(receiverPhone)
                .receiverAddress(receiverAddress)
                .remark(PENDING_PACKING_REMARK)
                .build();
    }

    private String resolveLogisticsNo(String logisticsNo) {
        if (logisticsNo != null && !logisticsNo.trim().isEmpty()) {
            return logisticsNo.trim();
        }
        return "SF" + IdUtil.fastSimpleUUID().substring(0, 18).toUpperCase();
    }
}
