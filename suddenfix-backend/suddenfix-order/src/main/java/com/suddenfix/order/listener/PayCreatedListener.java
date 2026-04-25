package com.suddenfix.order.listener;

import cn.hutool.json.JSONUtil;
import com.suddenfix.common.dto.PayCreatedMessage;
import com.suddenfix.order.config.OrderEventRabbitConfig;
import com.suddenfix.order.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PayCreatedListener {

    private final OrderMapper orderMapper;

    @RabbitListener(queues = OrderEventRabbitConfig.PAY_CREATED_QUEUE)
    public void onPayCreated(String payload) {
        PayCreatedMessage message = JSONUtil.toBean(payload, PayCreatedMessage.class);
        if (message.getOrderId() == null) {
            log.warn("【订单服务】支付单创建消息缺少 orderId: {}", payload);
            return;
        }

        int updateRow = orderMapper.updateOrderStatusToPendingPayment(message.getOrderId());
        if (updateRow > 0) {
            log.info("【订单服务】订单 {} 已进入待支付状态", message.getOrderId());
            return;
        }
        log.info("【订单服务】订单 {} 状态无需变更，可能已处理", message.getOrderId());
    }
}
