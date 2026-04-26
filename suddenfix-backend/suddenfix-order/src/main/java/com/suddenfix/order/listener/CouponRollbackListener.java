package com.suddenfix.order.listener;

import cn.hutool.json.JSONUtil;
import com.suddenfix.common.dto.CouponRollbackMessage;
import com.suddenfix.order.config.OrderEventRabbitConfig;
import com.suddenfix.order.domain.vo.CouponPreheatVO;
import com.suddenfix.order.mapper.CouponRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.suddenfix.common.enums.RedisPreMessage.COUPON_ROLLBACK_IDEMPOTENT;
import static com.suddenfix.common.enums.RedisPreMessage.COUPON_RESERVED;
import static com.suddenfix.common.enums.RedisPreMessage.COUPON_USER_SET;
import static com.suddenfix.common.enums.RedisPreMessage.COUPON_USER_TOKEN_HASH;

@Slf4j
@Component
@RequiredArgsConstructor
public class CouponRollbackListener {

    private static final DefaultRedisScript<Long> ROLLBACK_SCRIPT = buildRollbackScript();

    private final RedisTemplate<String, Object> redisTemplate;
    private final CouponRecordMapper couponRecordMapper;

    @RabbitListener(queues = OrderEventRabbitConfig.COUPON_ROLLBACK_QUEUE)
    public void onCouponRollback(String payload) {
        CouponRollbackMessage message = JSONUtil.toBean(payload, CouponRollbackMessage.class);
        if (message.getOrderId() == null || message.getCouponId() == null || message.getUserId() == null
                || message.getSegment() == null || message.getCouponToken() == null) {
            log.warn("【优惠券回滚】消息缺少必要字段，忽略: {}", payload);
            return;
        }

        couponRecordMapper.rollbackCouponUsedByOrderId(message.getOrderId());
        couponRecordMapper.clearCouponBindingByOrderId(message.getOrderId());

        Long result = redisTemplate.execute(
                ROLLBACK_SCRIPT,
                List.of(
                        COUPON_ROLLBACK_IDEMPOTENT.getValue() + message.getOrderId(),
                        COUPON_RESERVED.getValue() + message.getCouponId(),
                        COUPON_USER_SET.getValue() + message.getCouponId(),
                        COUPON_USER_TOKEN_HASH.getValue() + message.getCouponId()
                ),
                message.getCouponToken(),
                String.valueOf(message.getUserId()),
                JSONUtil.toJsonStr(CouponPreheatVO.builder()
                        .couponId(message.getCouponId())
                        .userId(message.getUserId())
                        .segment(message.getSegment())
                        .couponToken(message.getCouponToken())
                        .build())
        );

        if (Long.valueOf(1L).equals(result)) {
            log.info("【优惠券回滚】订单 {} 的优惠券 {} 已恢复给用户 {}",
                    message.getOrderId(), message.getCouponToken(), message.getUserId());
            return;
        }
        log.info("【优惠券回滚】订单 {} 已经补偿过，忽略重复消息", message.getOrderId());
    }

    private static DefaultRedisScript<Long> buildRollbackScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setResultType(Long.class);
        script.setScriptText("""
                if redis.call('exists', KEYS[1]) == 1 then
                    return 0
                end
                redis.call('set', KEYS[1], '1', 'EX', 604800)
                redis.call('hset', KEYS[4], ARGV[2], ARGV[3])
                redis.call('sadd', KEYS[3], ARGV[2])
                redis.call('hdel', KEYS[2], ARGV[1])
                return 1
                """);
        return script;
    }
}
