package com.suddenfix.order.listener;

import com.suddenfix.order.config.OrderEventRabbitConfig;
import com.suddenfix.order.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserCancelListener {

    private final OrderMapper orderMapper;

    @RabbitListener(queues = OrderEventRabbitConfig.USER_CANCELLED_QUEUE)
    public void onUserCancel(String payload) {

        try {
            Long userId = Long.parseLong(payload);

            int updateRows = orderMapper.desensitizeOrderUserInfo(userId);

            log.info("【订单服务】用户 {} 数据脱敏完成，共处理 {} 条历史订单", userId, updateRows);
        } catch (Exception e) {
            log.error("【订单服务】处理用户注销脱敏任务失败，userId: {}", payload, e);
            throw e;
        }
    }
}
