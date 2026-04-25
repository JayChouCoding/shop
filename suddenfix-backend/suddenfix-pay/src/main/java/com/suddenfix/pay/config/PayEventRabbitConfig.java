package com.suddenfix.pay.config;

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
public class PayEventRabbitConfig {

    public static final String ORDER_CREATED_QUEUE = "suddenfix.pay.TOPIC_ORDER_CREATED.queue";
    public static final String PAY_REFUND_QUEUE = "suddenfix.pay.TOPIC_PAY_REFUND.queue";

    @Bean
    public DirectExchange eventExchange() {
        return new DirectExchange(RabbitEventConstants.EVENT_EXCHANGE, true, false);
    }

    @Bean
    public Queue orderCreatedQueue() {
        return QueueBuilder.durable(ORDER_CREATED_QUEUE).build();
    }

    @Bean
    public Binding orderCreatedBinding(Queue orderCreatedQueue, DirectExchange eventExchange) {
        return BindingBuilder.bind(orderCreatedQueue).to(eventExchange).with(MsgTopic.TOPIC_ORDER_CREATED.getTopic());
    }

    @Bean
    public Queue payRefundQueue() {
        return QueueBuilder.durable(PAY_REFUND_QUEUE).build();
    }

    @Bean
    public Binding payRefundBinding(Queue payRefundQueue, DirectExchange eventExchange) {
        return BindingBuilder.bind(payRefundQueue).to(eventExchange).with(MsgTopic.TOPIC_PAY_REFUND.getTopic());
    }
}
