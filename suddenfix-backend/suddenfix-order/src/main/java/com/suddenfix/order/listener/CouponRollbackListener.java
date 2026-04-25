package com.suddenfix.order.listener;

import cn.hutool.json.JSONUtil;
import com.suddenfix.common.dto.CouponRollbackMessage;
import com.suddenfix.order.config.OrderEventRabbitConfig;
import com.suddenfix.order.mapper.CouponRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.suddenfix.common.enums.RedisPreMessage.COUPON_ROLLBACK_IDEMPOTENT;
import static com.suddenfix.common.enums.RedisPreMessage.COUPON_STOCK_SEGMENT;
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
                        COUPON_STOCK_SEGMENT.getValue() + message.getCouponId() + ":" + message.getSegment(),
                        COUPON_USER_SET.getValue() + message.getCouponId(),
                        COUPON_USER_TOKEN_HASH.getValue() + message.getCouponId()
                ),
                message.getCouponToken(),
                String.valueOf(message.getUserId())
        );

        // Lua 把“幂等标记 + token 归还 + 用户去重集合清理 + claim 明细删除”一次做完，
        // 即使 MQ 重投也不会把同一个 token 放回两次。
        if (Long.valueOf(1L).equals(result)) {
            log.info("【优惠券回滚】订单 {} 的 token {} 已归还到 segment {}",
                    message.getOrderId(), message.getCouponToken(), message.getSegment());
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
                redis.call('lpush', KEYS[2], ARGV[1])
                redis.call('srem', KEYS[3], ARGV[2])
                redis.call('hdel', KEYS[4], ARGV[2])
                return 1
                """);
        return script;
    }
}
