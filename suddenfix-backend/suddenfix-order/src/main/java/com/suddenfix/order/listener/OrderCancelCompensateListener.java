package com.suddenfix.order.listener;

import cn.hutool.json.JSONUtil;
import com.suddenfix.common.dto.CouponRollbackMessage;
import com.suddenfix.common.enums.MsgStatus;
import com.suddenfix.common.enums.OrderStatic;
import com.suddenfix.common.utils.GeneIdGenerator;
import com.suddenfix.order.config.OrderEventRabbitConfig;
import com.suddenfix.order.domain.pojo.CouponRecord;
import com.suddenfix.order.domain.pojo.Msg;
import com.suddenfix.order.domain.pojo.Order;
import com.suddenfix.order.domain.pojo.OrderItem;
import com.suddenfix.order.domain.pojo.OrderWithProduct;
import com.suddenfix.order.mapper.CouponRecordMapper;
import com.suddenfix.order.mapper.MsgMapper;
import com.suddenfix.order.mapper.OrderItemMapper;
import com.suddenfix.order.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import static com.suddenfix.common.enums.MsgTopic.TOPIC_COUPON_ROLLBACK;
import static com.suddenfix.common.enums.MsgTopic.TOPIC_RESTORE_STOCK;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCancelCompensateListener {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final CouponRecordMapper couponRecordMapper;
    private final MsgMapper msgMapper;

    @RabbitListener(queues = OrderEventRabbitConfig.ORDER_CANCEL_COMPENSATE_QUEUE)
    @Transactional(rollbackFor = Exception.class)
    public void cancelCompensate(String payload) {
        log.info("【订单补偿系统】收到超卖补偿消息，准备逆向取消订单 orderId: {}", payload);

        try {
            Long orderId = Long.parseLong(payload);

            // 1. 查出订单主表数据
            Order order = orderMapper.selectByOrderId(orderId);
            if (order == null) {
                log.warn("【订单补偿系统】订单 {} 不存在，放弃处理", orderId);
                return;
            }

            // 2. 状态校验：只有【待支付(10)】状态的订单才能被超卖补偿取消
            if (!OrderStatic.PENDING_PAYMENT.getCode().equals(order.getStatus())) {
                log.info("【订单补偿系统】订单 {} 状态非待支付(当前:{})，忽略补偿", orderId, order.getStatus());
                return;
            }

            // 3. 执行取消订单（使用你提供的 cancelOrder 接口，带着 userId 走单片精确路由）
            int row = orderMapper.cancelOrder(order.getUserId(), orderId);
            if (row <= 0) {
                log.warn("【订单补偿系统】订单 {} 更新为已关闭失败", orderId);
                return;
            }
            log.info("【订单补偿系统】订单 {} 已成功变更为【已关闭(50)】", orderId);

            // 4. 查询该订单购买的所有商品明细
            List<OrderItem> items = orderItemMapper.selectByOrderIdAndUserId(orderId, order.getUserId());
            
            // 5. 组装要发送给商品服务的恢复指令
            List<Long> productIds = items.stream().map(OrderItem::getProductId).collect(Collectors.toList());
            List<Long> quantities = items.stream().map(OrderItem::getQuantity).collect(Collectors.toList());

            OrderWithProduct restoreMsg = new OrderWithProduct();
            restoreMsg.setOrderId(orderId);
            restoreMsg.setProductIds(productIds);
            restoreMsg.setProductQuantities(quantities);

            insertLocalMessage(order.getUserId(), TOPIC_RESTORE_STOCK.getTopic(), JSONUtil.toJsonStr(restoreMsg));

            CouponRecord couponRecord = couponRecordMapper.selectByOrderId(orderId);
            if (couponRecord != null && couponRecord.getCouponId() != null
                    && couponRecord.getCouponToken() != null && couponRecord.getSegmentIndex() != null) {
                insertLocalMessage(order.getUserId(), TOPIC_COUPON_ROLLBACK.getTopic(), JSONUtil.toJsonStr(
                        CouponRollbackMessage.builder()
                                .orderId(orderId)
                                .userId(order.getUserId())
                                .couponId(couponRecord.getCouponId())
                                .segment(couponRecord.getSegmentIndex())
                                .couponToken(couponRecord.getCouponToken())
                                .build()
                ));
            }

        } catch (Exception e) {
            log.error("【订单补偿系统】处理超卖补偿异常，准备重试, orderId: {}", payload, e);
            throw e;
        }
    }

    private void insertLocalMessage(Long userId, String topic, String payload) {
        msgMapper.insertMsg(Msg.builder()
                .msgId(GeneIdGenerator.generatorId(userId))
                .businessId(GeneIdGenerator.generatorId(userId))
                .topic(topic)
                .payload(payload)
                .status(MsgStatus.PENDING_SENDING.getStatus())
                .retryCount(0)
                .nextRetryTime(new Date())
                .build());
    }
}
