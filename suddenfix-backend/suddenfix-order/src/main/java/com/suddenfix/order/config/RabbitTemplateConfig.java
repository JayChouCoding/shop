package com.suddenfix.order.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class RabbitTemplateConfig {

    private final RabbitTemplate rabbitTemplate;

    public RabbitTemplateConfig(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @PostConstruct
    public void init() {
        rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            if(ack){
                log.info("消息确认成功: {}", correlationData);
            }else {
                log.error("消息确认失败: {}, 原因: {}", correlationData, cause);
            }
        });

        rabbitTemplate.setReturnsCallback(returned -> {
            log.error("消息未路由到队列，已退回: {}", new String(returned.getMessage().getBody()));
        });
    }
}
