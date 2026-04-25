package com.suddenfix.pay.listener;

import cn.hutool.json.JSONUtil;
import com.suddenfix.common.dto.PayRollbackMessage;
import com.suddenfix.pay.config.PayEventRabbitConfig;
import com.suddenfix.pay.service.IPayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PayRefundListener {

    private final IPayService payService;

    @RabbitListener(queues = PayEventRabbitConfig.PAY_REFUND_QUEUE)
    public void onPayRefund(String payload) {
        PayRollbackMessage message = JSONUtil.toBean(payload, PayRollbackMessage.class);
        if (message.getOrderId() == null || message.getOutTradeNo() == null || message.getRefundAmount() == null) {
            log.warn("【支付服务】退款消息缺少必要字段: {}", payload);
            return;
        }
        payService.refundPay(message.getOutTradeNo(), message.getRefundAmount(), message.getOrderId());
    }
}
