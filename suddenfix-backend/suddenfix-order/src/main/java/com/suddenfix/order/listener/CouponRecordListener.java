package com.suddenfix.order.listener;

import cn.hutool.json.JSONUtil;
import com.suddenfix.order.domain.pojo.Coupon;
import com.suddenfix.order.domain.pojo.CouponRecord;
import com.suddenfix.order.domain.vo.CouponPreheatVO;
import com.suddenfix.order.mapper.CouponMapper;
import com.suddenfix.order.mapper.CouponRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Date;

@Slf4j
@Component
@RequiredArgsConstructor
public class CouponRecordListener {

    private final CouponRecordMapper couponRecordMapper;
    private final CouponMapper couponMapper;

    @RabbitListener(queues = CouponRabbitMQConfig.QUEUE_NAME)
    public void couponRecordListener(String message) {
        try {
            CouponPreheatVO couponPreheat = JSONUtil.toBean(message, CouponPreheatVO.class);
            Coupon coupon = couponMapper.selectCoupon(couponPreheat.getCouponId());

            CouponRecord couponRecord = CouponRecord.builder()
                    .couponId(coupon.getId())
                    .userId(couponPreheat.getUserId())
                    .segmentIndex(couponPreheat.getSegment())
                    .couponToken(couponPreheat.getCouponToken())
                    .status(0)
                    .createTime(new Date())
                    .build();

            couponRecordMapper.insertCouponRecord(couponRecord);
        } catch (Exception e) {
            log.error("【优惠券记录】消费预热消息失败: {}", message, e);
            throw e;
        }
    }
}
