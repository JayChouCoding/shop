package com.suddenfix.pay.listener;

import cn.hutool.json.JSONUtil;
import com.suddenfix.common.dto.OrderCreatedMessage;
import com.suddenfix.common.exception.ServiceException;
import com.suddenfix.pay.config.PayEventRabbitConfig;
import com.suddenfix.pay.service.IPayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCreatedListener {

    private final IPayService payService;

    @RabbitListener(queues = PayEventRabbitConfig.ORDER_CREATED_QUEUE)
    public void onOrderCreated(String payload) {
        log.info("【支付服务】收到订单创建消息 {}", payload);
        try {
            OrderCreatedMessage orderMessage = JSONUtil.toBean(payload, OrderCreatedMessage.class);
            Long orderId = orderMessage.getOrderId();
            Long userId = orderMessage.getUserId();
            Long payAmount = orderMessage.getPayAmount();
            Integer payChannel = orderMessage.getPayChannel();

            if (orderId == null || userId == null || payAmount == null) {
                throw new ServiceException("订单创建消息缺少支付初始化所需字段");
            }

            payService.initPayRecord(orderId, userId, payAmount, payChannel);
        } catch (Exception e) {
            log.error("【支付服务】消费订单创建消息失败 {}", payload, e);
            throw e;
        }
    }
}
