package com.suddenfix.product.config;

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
public class ProductEventRabbitConfig {

    public static final String STOCK_DEDUCTION_QUEUE = "suddenfix.product.TOPIC_STOCK_DEDUCTION.queue";
    public static final String RESTORE_STOCK_QUEUE = "suddenfix.product.TOPIC_RESTORE_STOCK.queue";

    @Bean
    public DirectExchange eventExchange() {
        return new DirectExchange(RabbitEventConstants.EVENT_EXCHANGE, true, false);
    }

    @Bean
    public Queue stockDeductionQueue() {
        return QueueBuilder.durable(STOCK_DEDUCTION_QUEUE).build();
    }

    @Bean
    public Binding stockDeductionBinding(Queue stockDeductionQueue, DirectExchange eventExchange) {
        return BindingBuilder.bind(stockDeductionQueue).to(eventExchange)
                .with(MsgTopic.TOPIC_STOCK_DEDUCTION.getTopic());
    }

    @Bean
    public Queue restoreStockQueue() {
        return QueueBuilder.durable(RESTORE_STOCK_QUEUE).build();
    }

    @Bean
    public Binding restoreStockBinding(Queue restoreStockQueue, DirectExchange eventExchange) {
        return BindingBuilder.bind(restoreStockQueue).to(eventExchange).with(MsgTopic.TOPIC_RESTORE_STOCK.getTopic());
    }
}
