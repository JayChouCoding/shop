package com.suddenfix.order.schedule;

import com.suddenfix.common.constants.RabbitEventConstants;
import com.suddenfix.order.domain.pojo.Msg;
import com.suddenfix.order.mapper.MsgMapper;
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

import static com.suddenfix.common.enums.RedisPreMessage.ORDER_SCHEDULE;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderMsgRelayJob {

    private final MsgMapper msgMapper;
    private final RedissonClient redissonClient;
    private final RabbitTemplate rabbitTemplate;

    private static final int BATCH_SIZE = 100;
    private static final int RELAY_COUNT = 5;
    private static final int RELAY_TIME = 10000;


    @Scheduled(cron = "0/1 * * * * ?")
    public void relayMessages(){
        RLock lock = redissonClient.getLock(ORDER_SCHEDULE.getValue());
        boolean locked = false;
        try {
            locked = lock.tryLock(0, 10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("【订单服务】本地消息投递任务获取分布式锁被中断");
            return;
        }
        if (!locked) {
            return;
        }

        try {
            List<Msg> msgList = msgMapper.selectPendingMsg(new Date(), BATCH_SIZE, RELAY_COUNT);

            if(msgList == null || msgList.isEmpty()){
                return;
            }

            log.info("【订单服务】本地消息投递任务启动，扫描到 {} 条待发送消息", msgList.size());

            for (Msg msg : msgList) {
                if(msg.getRetryCount() >= RELAY_COUNT){
                    log.error("【订单服务】消息重试超限，已标记为死信，msgId={}", msg.getMsgId());
                    msgMapper.updateMsgDead(msg.getMsgId(),msg.getBusinessId());
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
                            log.info("【订单服务】本地消息投递成功，msgId={}, topic={}", msg.getMsgId(), msg.getTopic());
                            msgMapper.updateMsgSend(msg.getMsgId(),msg.getBusinessId());
                            return;
                        }
                        Date nextRelayTime = new Date(System.currentTimeMillis() + (long) (msg.getRetryCount() + 1) * RELAY_TIME);
                        log.warn("【订单服务】本地消息投递失败，msgId={}, topic={}, nextRetryTime={}, reason={}",
                                msg.getMsgId(), msg.getTopic(), nextRelayTime,
                                confirm == null ? (ex == null ? "unknown" : ex.getMessage()) : confirm.getReason(),
                                ex);
                        msgMapper.updateMsgRelay(msg.getMsgId(),msg.getBusinessId(),nextRelayTime);
                    });
                } catch (Exception ex) {
                    Date nextRelayTime = new Date(System.currentTimeMillis() + (long) (msg.getRetryCount() + 1) * RELAY_TIME);
                    log.warn("【订单服务】本地消息发送异常，msgId={}, topic={}, nextRetryTime={}",
                            msg.getMsgId(), msg.getTopic(), nextRelayTime, ex);
                    msgMapper.updateMsgRelay(msg.getMsgId(),msg.getBusinessId(),nextRelayTime);
                }
            }
        }catch (Exception e){
            log.error("【订单服务】执行本地消息轮询任务发生异常", e);
        }finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
