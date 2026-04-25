package com.suddenfix.order.config;

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
public class OrderEventRabbitConfig {

    public static final String ON_CREATE_QUEUE = "suddenfix.order.TOPIC_ON_CREATE.queue";
    public static final String ORDER_CANCEL_QUEUE = "suddenfix.order.TOPIC_ORDER_CANCEL.queue";
    public static final String ORDER_CANCEL_COMPENSATE_QUEUE = "suddenfix.order.TOPIC_ORDER_CANCEL_COMPENSATE.queue";
    public static final String PAY_CREATED_QUEUE = "suddenfix.order.TOPIC_PAY_CREATED.queue";
    public static final String PAY_SUCCESS_QUEUE = "suddenfix.order.TOPIC_PAY_SUCCESS.queue";
    public static final String USER_CANCELLED_QUEUE = "suddenfix.order.TOPIC_USER_CANCELLED.queue";
    public static final String COUPON_ROLLBACK_QUEUE = "suddenfix.order.TOPIC_COUPON_ROLLBACK.queue";

    @Bean
    public DirectExchange eventExchange() {
        return new DirectExchange(RabbitEventConstants.EVENT_EXCHANGE, true, false);
    }

    @Bean
    public Queue onCreateQueue() {
        return QueueBuilder.durable(ON_CREATE_QUEUE).build();
    }

    @Bean
    public Binding onCreateBinding(Queue onCreateQueue, DirectExchange eventExchange) {
        return BindingBuilder.bind(onCreateQueue).to(eventExchange).with(MsgTopic.TOPIC_ON_CREATE.getTopic());
    }

    @Bean
    public Queue orderCancelQueue() {
        return QueueBuilder.durable(ORDER_CANCEL_QUEUE).build();
    }

    @Bean
    public Binding orderCancelBinding(Queue orderCancelQueue, DirectExchange eventExchange) {
        return BindingBuilder.bind(orderCancelQueue).to(eventExchange).with(MsgTopic.TOPIC_ORDER_CANCEL.getTopic());
    }

    @Bean
    public Queue orderCancelCompensateQueue() {
        return QueueBuilder.durable(ORDER_CANCEL_COMPENSATE_QUEUE).build();
    }

    @Bean
    public Binding orderCancelCompensateBinding(Queue orderCancelCompensateQueue, DirectExchange eventExchange) {
        return BindingBuilder.bind(orderCancelCompensateQueue).to(eventExchange)
                .with(MsgTopic.TOPIC_ORDER_CANCEL_COMPENSATE.getTopic());
    }

    @Bean
    public Queue payCreatedQueue() {
        return QueueBuilder.durable(PAY_CREATED_QUEUE).build();
    }

    @Bean
    public Binding payCreatedBinding(Queue payCreatedQueue, DirectExchange eventExchange) {
        return BindingBuilder.bind(payCreatedQueue).to(eventExchange).with(MsgTopic.TOPIC_PAY_CREATED.getTopic());
    }

    @Bean
    public Queue paySuccessQueue() {
        return QueueBuilder.durable(PAY_SUCCESS_QUEUE).build();
    }

    @Bean
    public Binding paySuccessBinding(Queue paySuccessQueue, DirectExchange eventExchange) {
        return BindingBuilder.bind(paySuccessQueue).to(eventExchange).with(MsgTopic.TOPIC_PAY_SUCCESS.getTopic());
    }

    @Bean
    public Queue userCancelledQueue() {
        return QueueBuilder.durable(USER_CANCELLED_QUEUE).build();
    }

    @Bean
    public Binding userCancelledBinding(Queue userCancelledQueue, DirectExchange eventExchange) {
        return BindingBuilder.bind(userCancelledQueue).to(eventExchange).with(MsgTopic.TOPIC_USER_CANCELLED.getTopic());
    }

    @Bean
    public Queue couponRollbackQueue() {
        return QueueBuilder.durable(COUPON_ROLLBACK_QUEUE).build();
    }

    @Bean
    public Binding couponRollbackBinding(Queue couponRollbackQueue, DirectExchange eventExchange) {
        return BindingBuilder.bind(couponRollbackQueue).to(eventExchange)
                .with(MsgTopic.TOPIC_COUPON_ROLLBACK.getTopic());
    }
}
