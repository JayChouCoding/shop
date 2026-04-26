package com.suddenfix.order.listener;

import cn.hutool.json.JSONUtil;
import com.suddenfix.common.constants.RabbitEventConstants;
import com.suddenfix.common.dto.CouponRollbackMessage;
import com.suddenfix.common.enums.MsgStatus;
import com.suddenfix.order.config.OrderEventRabbitConfig;
import com.suddenfix.order.domain.pojo.CouponRecord;
import com.suddenfix.order.domain.pojo.Msg;
import com.suddenfix.order.domain.pojo.OrderWithProduct;
import com.suddenfix.order.mapper.CouponRecordMapper;
import com.suddenfix.order.mapper.MsgMapper;
import com.suddenfix.order.mapper.OrderMapper;
import com.suddenfix.common.utils.GeneIdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

import static com.suddenfix.common.enums.MsgTopic.TOPIC_COUPON_ROLLBACK;
import static com.suddenfix.common.enums.MsgTopic.TOPIC_RESTORE_STOCK;
import static com.suddenfix.common.enums.OrderStatic.PENDING_PAYMENT;

@Component
@Slf4j
@RequiredArgsConstructor
public class OrderTimeoutCancelListener {

    private final OrderMapper orderMapper;
    private final CouponRecordMapper couponRecordMapper;
    private final MsgMapper msgMapper;

    @RabbitListener(queues = OrderEventRabbitConfig.ORDER_CANCEL_QUEUE)
    @Transactional(rollbackFor = Exception.class)
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

            insertLocalMessage(userId, TOPIC_RESTORE_STOCK.getTopic(), payload);

            CouponRecord couponRecord = couponRecordMapper.selectByOrderId(orderId);
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
                insertLocalMessage(userId, TOPIC_COUPON_ROLLBACK.getTopic(), JSONUtil.toJsonStr(rollbackMessage));
            }
        } catch (Exception e) {
            log.error("【订单服务】处理订单取消消息异常", e);
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
