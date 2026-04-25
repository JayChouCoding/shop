package com.suddenfix.order.listener;

import cn.hutool.json.JSONUtil;
import com.suddenfix.common.constants.RabbitEventConstants;
import com.suddenfix.common.dto.CouponRollbackMessage;
import com.suddenfix.order.config.OrderEventRabbitConfig;
import com.suddenfix.order.domain.pojo.CouponRecord;
import com.suddenfix.order.domain.pojo.OrderItem;
import com.suddenfix.order.domain.pojo.OrderWithProduct;
import com.suddenfix.order.mapper.CouponRecordMapper;
import com.suddenfix.order.mapper.OrderItemMapper;
import com.suddenfix.order.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.suddenfix.common.enums.MsgTopic.TOPIC_COUPON_ROLLBACK;
import static com.suddenfix.common.enums.MsgTopic.TOPIC_RESTORE_STOCK;
import static com.suddenfix.common.enums.OrderStatic.PENDING_PAYMENT;
import static com.suddenfix.common.enums.RedisPreMessage.GOODS_PRE_DEDUCTION;
import static com.suddenfix.common.enums.RedisPreMessage.GOODS_IS_EXIST;
import static com.suddenfix.common.enums.RedisPreMessage.ORDER_STOCK_RESTORED;

@Component
@Slf4j
@RequiredArgsConstructor
public class OrderTimeoutCancelListener {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final CouponRecordMapper couponRecordMapper;
    private final RabbitTemplate rabbitTemplate;
    private final RedisTemplate<String, Object> redisTemplate;

    @RabbitListener(queues = OrderEventRabbitConfig.ORDER_CANCEL_QUEUE)
    public void onOrderTimeout(String payload) {
        log.info("【订单服务】收到订单取消消息: {}", payload);

        try {
            OrderWithProduct orderWithProduct = JSONUtil.toBean(payload, OrderWithProduct.class);
            Long orderId = orderWithProduct.getOrderId();
            Long userId = orderWithProduct.getUserId();
            if (orderId == null || userId == null) {
                log.warn("【订单服务】取消消息缺少必要字段，忽略: {}", payload);
                return;
            }

            Integer status = orderMapper.selectStatusByOrderIdAndUserId(orderId, userId);
            if (status == null || !status.equals(PENDING_PAYMENT.getCode())) {
                log.info("【订单服务】订单 {} 当前状态 {}，无需取消", orderId, status);
                return;
            }

            int updateRow = orderMapper.cancelOrder(userId, orderId);
            if (updateRow <= 0) {
                log.info("【订单服务】订单 {} 取消失败或已被处理", orderId);
                return;
            }

            List<OrderItem> items = orderItemMapper.selectByOrderIdAndUserId(orderId, userId);
            for (OrderItem item : items) {
                String stockKey = GOODS_PRE_DEDUCTION.getValue() + item.getProductId();
                if (Boolean.TRUE.equals(redisTemplate.hasKey(stockKey))) {
                    redisTemplate.opsForValue().increment(stockKey, item.getQuantity());
                } else {
                    redisTemplate.opsForValue().set(stockKey, item.getQuantity(), 1, TimeUnit.DAYS);
                }
                redisTemplate.opsForValue().set(GOODS_IS_EXIST.getValue() + item.getProductId(), 1, 1, TimeUnit.DAYS);
            }
            redisTemplate.opsForValue().set(ORDER_STOCK_RESTORED.getValue() + orderId, "1", 1, TimeUnit.DAYS);

            rabbitTemplate.convertAndSend(RabbitEventConstants.EVENT_EXCHANGE, TOPIC_RESTORE_STOCK.getTopic(), payload);

            CouponRecord couponRecord = couponRecordMapper.selectByOrderId(orderId);
            couponRecordMapper.clearCouponBindingByOrderId(orderId);
            Long couponId = couponRecord == null ? null : couponRecord.getCouponId();
            String couponToken = couponRecord == null ? null : couponRecord.getCouponToken();
            Integer couponSegment = couponRecord == null ? null : couponRecord.getSegmentIndex();
            if (couponId != null && couponToken != null && couponSegment != null) {
                CouponRollbackMessage rollbackMessage = CouponRollbackMessage.builder()
                        .orderId(orderId)
                        .userId(userId)
                        .couponId(couponId)
                        .segment(couponSegment)
                        .couponToken(couponToken)
                        .build();
                rabbitTemplate.convertAndSend(
                        RabbitEventConstants.EVENT_EXCHANGE,
                        TOPIC_COUPON_ROLLBACK.getTopic(),
                        JSONUtil.toJsonStr(rollbackMessage)
                );
            }
        } catch (Exception e) {
            log.error("【订单服务】处理订单取消消息异常", e);
            throw e;
        }
    }
}
