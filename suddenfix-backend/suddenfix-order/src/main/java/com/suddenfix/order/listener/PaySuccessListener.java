package com.suddenfix.order.listener;

import cn.hutool.json.JSONUtil;
import com.suddenfix.common.dto.CouponRollbackMessage;
import com.suddenfix.common.dto.OrderPaidMessage;
import com.suddenfix.common.dto.PayRollbackMessage;
import com.suddenfix.common.dto.PaySuccessMessage;
import com.suddenfix.common.enums.MsgStatus;
import com.suddenfix.common.enums.MsgTopic;
import com.suddenfix.common.enums.OrderStatic;
import com.suddenfix.common.utils.GeneIdGenerator;
import com.suddenfix.order.config.OrderEventRabbitConfig;
import com.suddenfix.order.domain.pojo.CouponRecord;
import com.suddenfix.order.domain.pojo.Msg;
import com.suddenfix.order.domain.pojo.Order;
import com.suddenfix.order.domain.pojo.OrderItem;
import com.suddenfix.order.mapper.CouponRecordMapper;
import com.suddenfix.order.mapper.MsgMapper;
import com.suddenfix.order.mapper.OrderItemMapper;
import com.suddenfix.order.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.suddenfix.common.enums.RedisPreMessage.GOODS_PRE_DEDUCTION;
import static com.suddenfix.common.enums.RedisPreMessage.ORDER_PAY_COMPENSATE;
import static com.suddenfix.common.enums.RedisPreMessage.ORDER_STOCK_RESTORED;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaySuccessListener {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final CouponRecordMapper couponRecordMapper;
    private final MsgMapper msgMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    @RabbitListener(queues = OrderEventRabbitConfig.PAY_SUCCESS_QUEUE)
    @Transactional(rollbackFor = Exception.class)
    public void onPaySuccess(String payload) {
        PaySuccessMessage paySuccessMessage = JSONUtil.toBean(payload, PaySuccessMessage.class);
        Long orderId = paySuccessMessage.getOrderId();
        String outTradeNo = paySuccessMessage.getOutTradeNo();
        if (orderId == null || outTradeNo == null || outTradeNo.isBlank()) {
            log.warn("【订单服务】支付成功消息缺少核心字段 {}", payload);
            return;
        }

        Order order = orderMapper.selectByOrderId(orderId);
        if (order == null) {
            log.warn("【订单服务】订单 {} 不存在，忽略支付成功消息", orderId);
            return;
        }

        if (OrderStatic.PENDING_PAYMENT.getCode().equals(order.getStatus())) {
            int updateRow = orderMapper.updateOrderStatusToPaid(orderId, outTradeNo);
            if (updateRow > 0) {
                couponRecordMapper.markCouponUsedByOrderId(orderId);
                insertLocalMessage(order.getUserId(), MsgTopic.TOPIC_ORDER_PAID.getTopic(), JSONUtil.toJsonStr(
                        OrderPaidMessage.builder()
                                .orderId(orderId)
                                .userId(order.getUserId())
                                .payAmount(order.getPayAmount())
                                .outTradeNo(outTradeNo)
                                .receiverName(order.getReceiverName())
                                .receiverPhone(order.getReceiverPhone())
                                .receiverAddress(order.getReceiverAddress())
                                .build()
                ));
                log.info("【订单服务】订单 {} 已更新为已支付，并发送 TOPIC_ORDER_PAID", orderId);
            }
            return;
        }

        if (!OrderStatic.CLOSED.getCode().equals(order.getStatus())) {
            log.info("【订单服务】订单 {} 当前状态为 {}，无需支付补偿", orderId, order.getStatus());
            return;
        }

        String compensateKey = ORDER_PAY_COMPENSATE.getValue() + orderId;
        Boolean firstCompensate = redisTemplate.opsForValue().setIfAbsent(compensateKey, "1", 1, TimeUnit.DAYS);
        if (Boolean.FALSE.equals(firstCompensate)) {
            log.info("【订单服务】订单 {} 已处理过晚到支付补偿", orderId);
            return;
        }

        String restoredKey = ORDER_STOCK_RESTORED.getValue() + orderId;
        if (!Boolean.TRUE.equals(redisTemplate.hasKey(restoredKey))) {
            List<OrderItem> items = orderItemMapper.selectByOrderIdAndUserId(orderId, order.getUserId());
            for (OrderItem item : items) {
                String stockKey = GOODS_PRE_DEDUCTION.getValue() + item.getProductId();
                if (Boolean.TRUE.equals(redisTemplate.hasKey(stockKey))) {
                    redisTemplate.opsForValue().increment(stockKey, item.getQuantity());
                } else {
                    redisTemplate.opsForValue().set(stockKey, item.getQuantity(), 1, TimeUnit.DAYS);
                }
            }
            redisTemplate.opsForValue().set(restoredKey, "1", 1, TimeUnit.DAYS);
        }

        insertLocalMessage(order.getUserId(), MsgTopic.TOPIC_PAY_REFUND.getTopic(), JSONUtil.toJsonStr(
                PayRollbackMessage.builder()
                        .orderId(orderId)
                        .outTradeNo(outTradeNo)
                        .refundAmount(order.getPayAmount())
                        .refundNo("REFUND_" + orderId)
                        .build()
        ));

        CouponRecord couponRecord = couponRecordMapper.selectByOrderId(orderId);
        if (couponRecord != null) {
            insertLocalMessage(order.getUserId(), MsgTopic.TOPIC_COUPON_ROLLBACK.getTopic(), JSONUtil.toJsonStr(
                    CouponRollbackMessage.builder()
                            .orderId(orderId)
                            .userId(order.getUserId())
                            .couponId(couponRecord.getCouponId())
                            .segment(couponRecord.getSegmentIndex())
                            .couponToken(couponRecord.getCouponToken())
                            .build()
            ));
        }

        log.info("【订单服务】订单 {} 处于已取消状态，已写入退款与优惠券回滚消息", orderId);
    }

    private void insertLocalMessage(Long userId, String topic, String payload) {
        Msg msg = Msg.builder()
                .msgId(GeneIdGenerator.generatorId(userId))
                .businessId(GeneIdGenerator.generatorId(userId))
                .topic(topic)
                .payload(payload)
                .status(MsgStatus.PENDING_SENDING.getStatus())
                .retryCount(0)
                .nextRetryTime(new Date())
                .build();
        msgMapper.insertMsg(msg);
    }
}
