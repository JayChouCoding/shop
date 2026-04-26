package com.suddenfix.product.schedule;

import com.suddenfix.common.constants.RabbitEventConstants;
import com.suddenfix.product.domain.pojo.Msg;
import com.suddenfix.product.mapper.MsgMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.suddenfix.common.enums.RedisPreMessage.PRODUCT_SCHEDULE;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductMsgRelayJob {

    private static final int BATCH_SIZE = 100;
    private static final int RELAY_COUNT = 5;
    private static final int RELAY_TIME = 10000;
    private static final String PRODUCT_MSG_LOCK = PRODUCT_SCHEDULE.getValue();

    private final MsgMapper msgMapper;
    private final RedissonClient redissonClient;
    private final RabbitTemplate rabbitTemplate;

    @Scheduled(cron = "0/1 * * * * ?")
    public void relayMessages() {
        RLock lock = redissonClient.getLock(PRODUCT_MSG_LOCK);
        boolean locked = false;
        try {
            locked = lock.tryLock(0, 10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("【商品服务】本地消息投递任务获取分布式锁被中断");
            return;
        }
        if (!locked) {
            return;
        }

        try {
            List<Msg> msgList = msgMapper.selectPendingMsg(new Date(), BATCH_SIZE, RELAY_COUNT);
            if (msgList == null || msgList.isEmpty()) {
                return;
            }

            for (Msg msg : msgList) {
                if (msg.getRetryCount() >= RELAY_COUNT) {
                    msgMapper.updateMsgDead(msg.getMsgId(), msg.getBusinessId());
                    continue;
                }
                try {
                    CorrelationData correlationData = new CorrelationData(String.valueOf(msg.getMsgId()));
                    rabbitTemplate.convertAndSend(
                            RabbitEventConstants.EVENT_EXCHANGE,
                            msg.getTopic(),
                            msg.getPayload(),
                            correlationData
                    );
                    correlationData.getFuture().whenComplete((confirm, ex) -> {
                        if (ex == null && confirm != null && confirm.isAck()) {
                            msgMapper.updateMsgSend(msg.getMsgId(), msg.getBusinessId());
                            return;
                        }
                        Date nextRelayTime = new Date(System.currentTimeMillis() + (long) (msg.getRetryCount() + 1) * RELAY_TIME);
                        msgMapper.updateMsgRelay(msg.getMsgId(), msg.getBusinessId(), nextRelayTime);
                    });
                } catch (Exception ex) {
                    Date nextRelayTime = new Date(System.currentTimeMillis() + (long) (msg.getRetryCount() + 1) * RELAY_TIME);
                    msgMapper.updateMsgRelay(msg.getMsgId(), msg.getBusinessId(), nextRelayTime);
                }
            }
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
