package com.suddenfix.shipping.listener;

import cn.hutool.core.util.IdUtil;
import cn.hutool.json.JSONUtil;
import com.suddenfix.common.dto.OrderPaidMessage;
import com.suddenfix.shipping.config.ShippingEventRabbitConfig;
import com.suddenfix.shipping.domain.pojo.ShippingRecord;
import com.suddenfix.shipping.service.IShippingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderPaidListener {

    private final IShippingService shippingService;

    @RabbitListener(queues = ShippingEventRabbitConfig.ORDER_PAID_QUEUE)
    public void onOrderPaid(String payload) {
        OrderPaidMessage orderPaidMessage = JSONUtil.toBean(payload, OrderPaidMessage.class);
        if (orderPaidMessage.getOrderId() == null || orderPaidMessage.getUserId() == null) {
            log.warn("【物流服务】订单支付成功消息缺少核心字段 {}", payload);
            return;
        }

        shippingService.initPendingShipping(ShippingRecord.builder()
                .shippingId(IdUtil.getSnowflakeNextId())
                .orderId(orderPaidMessage.getOrderId())
                .userId(orderPaidMessage.getUserId())
                .logisticsNo(null)
                .expressCompany("等待商家打包")
                .shippingStatus(0)
                .receiverName(orderPaidMessage.getReceiverName())
                .receiverPhone(orderPaidMessage.getReceiverPhone())
                .receiverAddress(orderPaidMessage.getReceiverAddress())
                .remark("订单已支付成功，商家正在为你打包发货")
                .build());

        log.info("【物流服务】订单 {} 已初始化待发货物流单", orderPaidMessage.getOrderId());
    }
}
