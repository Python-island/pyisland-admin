package com.pyisland.server.user.payment.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 支付回调消息队列配置。
 */
@Configuration
@EnableRabbit
public class PaymentMqConfig {

    public static final String PAYMENT_NOTIFY_EXCHANGE = "eisland.payment.notify.exchange";
    public static final String PAYMENT_NOTIFY_QUEUE = "eisland.payment.notify.process.queue";
    public static final String PAYMENT_NOTIFY_ROUTING_KEY = "eisland.payment.notify.process";

    @Bean
    public DirectExchange paymentNotifyExchange() {
        return new DirectExchange(PAYMENT_NOTIFY_EXCHANGE, true, false);
    }

    @Bean
    public Queue paymentNotifyQueue() {
        return new Queue(PAYMENT_NOTIFY_QUEUE, true);
    }

    @Bean
    public Binding paymentNotifyBinding(Queue paymentNotifyQueue, DirectExchange paymentNotifyExchange) {
        return BindingBuilder.bind(paymentNotifyQueue).to(paymentNotifyExchange).with(PAYMENT_NOTIFY_ROUTING_KEY);
    }
}
