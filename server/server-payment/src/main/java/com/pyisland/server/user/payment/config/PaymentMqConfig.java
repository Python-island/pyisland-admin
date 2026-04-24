package com.pyisland.server.user.payment.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.beans.factory.annotation.Value;
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
    public static final String PAYMENT_NOTIFY_RETRY_QUEUE = "eisland.payment.notify.retry.queue";
    public static final String PAYMENT_NOTIFY_RETRY_ROUTING_KEY = "eisland.payment.notify.retry";
    public static final String PAYMENT_NOTIFY_DLQ = "eisland.payment.notify.dlq";
    public static final String PAYMENT_NOTIFY_DLQ_ROUTING_KEY = "eisland.payment.notify.dlq";
    public static final String PAYMENT_RETRY_HEADER = "x-payment-retry-count";
    public static final String PAYMENT_RECEIPT_QUEUE = "eisland.payment.receipt.dispatch.queue";
    public static final String PAYMENT_RECEIPT_ROUTING_KEY = "eisland.payment.receipt.dispatch";
    public static final String PAYMENT_RECEIPT_RETRY_QUEUE = "eisland.payment.receipt.retry.queue";
    public static final String PAYMENT_RECEIPT_RETRY_ROUTING_KEY = "eisland.payment.receipt.retry";
    public static final String PAYMENT_RECEIPT_DLQ = "eisland.payment.receipt.dlq";
    public static final String PAYMENT_RECEIPT_DLQ_ROUTING_KEY = "eisland.payment.receipt.dlq";
    public static final String PAYMENT_RECEIPT_RETRY_HEADER = "x-payment-receipt-retry-count";

    @Bean
    public DirectExchange paymentNotifyExchange() {
        return new DirectExchange(PAYMENT_NOTIFY_EXCHANGE, true, false);
    }

    @Bean
    public Queue paymentNotifyQueue() {
        return QueueBuilder.durable(PAYMENT_NOTIFY_QUEUE).build();
    }

    @Bean
    public Queue paymentNotifyRetryQueue(@Value("${payment.notify-retry-delay-ms:15000}") long retryDelayMs) {
        return QueueBuilder.durable(PAYMENT_NOTIFY_RETRY_QUEUE)
                .withArgument("x-message-ttl", retryDelayMs)
                .withArgument("x-dead-letter-exchange", PAYMENT_NOTIFY_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", PAYMENT_NOTIFY_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue paymentNotifyDlqQueue() {
        return QueueBuilder.durable(PAYMENT_NOTIFY_DLQ).build();
    }

    @Bean
    public Queue paymentReceiptQueue() {
        return QueueBuilder.durable(PAYMENT_RECEIPT_QUEUE).build();
    }

    @Bean
    public Queue paymentReceiptRetryQueue(@Value("${payment.notify-retry-delay-ms:15000}") long retryDelayMs) {
        return QueueBuilder.durable(PAYMENT_RECEIPT_RETRY_QUEUE)
                .withArgument("x-message-ttl", retryDelayMs)
                .withArgument("x-dead-letter-exchange", PAYMENT_NOTIFY_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", PAYMENT_RECEIPT_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue paymentReceiptDlqQueue() {
        return QueueBuilder.durable(PAYMENT_RECEIPT_DLQ).build();
    }

    @Bean
    public Binding paymentNotifyBinding(Queue paymentNotifyQueue, DirectExchange paymentNotifyExchange) {
        return BindingBuilder.bind(paymentNotifyQueue).to(paymentNotifyExchange).with(PAYMENT_NOTIFY_ROUTING_KEY);
    }

    @Bean
    public Binding paymentNotifyRetryBinding(DirectExchange paymentNotifyExchange, Queue paymentNotifyRetryQueue) {
        return BindingBuilder.bind(paymentNotifyRetryQueue).to(paymentNotifyExchange).with(PAYMENT_NOTIFY_RETRY_ROUTING_KEY);
    }

    @Bean
    public Binding paymentNotifyDlqBinding(DirectExchange paymentNotifyExchange, Queue paymentNotifyDlqQueue) {
        return BindingBuilder.bind(paymentNotifyDlqQueue).to(paymentNotifyExchange).with(PAYMENT_NOTIFY_DLQ_ROUTING_KEY);
    }

    @Bean
    public Binding paymentReceiptBinding(DirectExchange paymentNotifyExchange, Queue paymentReceiptQueue) {
        return BindingBuilder.bind(paymentReceiptQueue).to(paymentNotifyExchange).with(PAYMENT_RECEIPT_ROUTING_KEY);
    }

    @Bean
    public Binding paymentReceiptRetryBinding(DirectExchange paymentNotifyExchange, Queue paymentReceiptRetryQueue) {
        return BindingBuilder.bind(paymentReceiptRetryQueue).to(paymentNotifyExchange).with(PAYMENT_RECEIPT_RETRY_ROUTING_KEY);
    }

    @Bean
    public Binding paymentReceiptDlqBinding(DirectExchange paymentNotifyExchange, Queue paymentReceiptDlqQueue) {
        return BindingBuilder.bind(paymentReceiptDlqQueue).to(paymentNotifyExchange).with(PAYMENT_RECEIPT_DLQ_ROUTING_KEY);
    }
}
