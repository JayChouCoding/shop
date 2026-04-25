package com.suddenfix.shipping.config;

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
public class ShippingEventRabbitConfig {

    public static final String ORDER_PAID_QUEUE = "suddenfix.shipping.TOPIC_ORDER_PAID.queue";

    @Bean
    public DirectExchange eventExchange() {
        return new DirectExchange(RabbitEventConstants.EVENT_EXCHANGE, true, false);
    }

    @Bean
    public Queue orderPaidQueue() {
        return QueueBuilder.durable(ORDER_PAID_QUEUE).build();
    }

    @Bean
    public Binding orderPaidBinding(Queue orderPaidQueue, DirectExchange eventExchange) {
        return BindingBuilder.bind(orderPaidQueue).to(eventExchange).with(MsgTopic.TOPIC_ORDER_PAID.getTopic());
    }
}
