package com.suddenfix.user.config;

import com.suddenfix.common.constants.RabbitEventConstants;
import com.suddenfix.common.enums.MsgTopic;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UserEventRabbitConfig {

    public static final String USER_CANCELLED_QUEUE = "suddenfix.user.TOPIC_USER_CANCELLED.queue";

    @Bean
    public DirectExchange eventExchange() {
        return new DirectExchange(RabbitEventConstants.EVENT_EXCHANGE, true, false);
    }

    @Bean
    public Queue userCancelledQueue() {
        return QueueBuilder.durable(USER_CANCELLED_QUEUE).build();
    }

    @Bean
    public Binding userCancelledBinding(Queue userCancelledQueue, DirectExchange eventExchange) {
        return BindingBuilder.bind(userCancelledQueue).to(eventExchange).with(MsgTopic.TOPIC_USER_CANCELLED.getTopic());
    }
}
