package com.pyisland.server.upload.config;

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
 * 对象复制任务 MQ 配置。
 */
@Configuration
@EnableRabbit
public class ObjectReplicationMqConfig {

    public static final String REPLICATION_EXCHANGE = "eisland.object.replication.exchange";
    public static final String REPLICATION_QUEUE = "eisland.object.replication.process.queue";
    public static final String REPLICATION_ROUTING_KEY = "eisland.object.replication.process";
    public static final String REPLICATION_RETRY_QUEUE = "eisland.object.replication.retry.queue";
    public static final String REPLICATION_RETRY_ROUTING_KEY = "eisland.object.replication.retry";
    public static final String REPLICATION_DLQ = "eisland.object.replication.dlq";
    public static final String REPLICATION_DLQ_ROUTING_KEY = "eisland.object.replication.dlq";
    public static final String RETRY_HEADER = "x-object-replication-retry-count";

    @Bean
    public DirectExchange objectReplicationExchange() {
        return new DirectExchange(REPLICATION_EXCHANGE, true, false);
    }

    @Bean
    public Queue objectReplicationQueue() {
        return QueueBuilder.durable(REPLICATION_QUEUE)
                .withArgument("x-max-priority", 10)
                .build();
    }

    @Bean
    public Queue objectReplicationRetryQueue(@Value("${object-replication.retry-delay-ms:15000}") long retryDelayMs) {
        return QueueBuilder.durable(REPLICATION_RETRY_QUEUE)
                .withArgument("x-message-ttl", retryDelayMs)
                .withArgument("x-dead-letter-exchange", REPLICATION_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", REPLICATION_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue objectReplicationDlqQueue() {
        return QueueBuilder.durable(REPLICATION_DLQ).build();
    }

    @Bean
    public Binding objectReplicationBinding(Queue objectReplicationQueue, DirectExchange objectReplicationExchange) {
        return BindingBuilder.bind(objectReplicationQueue)
                .to(objectReplicationExchange)
                .with(REPLICATION_ROUTING_KEY);
    }

    @Bean
    public Binding objectReplicationRetryBinding(Queue objectReplicationRetryQueue,
                                                 DirectExchange objectReplicationExchange) {
        return BindingBuilder.bind(objectReplicationRetryQueue)
                .to(objectReplicationExchange)
                .with(REPLICATION_RETRY_ROUTING_KEY);
    }

    @Bean
    public Binding objectReplicationDlqBinding(Queue objectReplicationDlqQueue,
                                               DirectExchange objectReplicationExchange) {
        return BindingBuilder.bind(objectReplicationDlqQueue)
                .to(objectReplicationExchange)
                .with(REPLICATION_DLQ_ROUTING_KEY);
    }
}
