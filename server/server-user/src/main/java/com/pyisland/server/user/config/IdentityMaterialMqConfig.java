package com.pyisland.server.user.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 身份认证人脸素材异步上传消息队列配置。
 */
@Configuration
@EnableRabbit
public class IdentityMaterialMqConfig {

    public static final String EXCHANGE = "eisland.identity.material.exchange";
    public static final String QUEUE = "eisland.identity.material.upload.queue";
    public static final String ROUTING_KEY = "eisland.identity.material.upload";
    public static final String RETRY_QUEUE = "eisland.identity.material.upload.retry.queue";
    public static final String RETRY_ROUTING_KEY = "eisland.identity.material.upload.retry";
    public static final String DLQ = "eisland.identity.material.upload.dlq";
    public static final String DLQ_ROUTING_KEY = "eisland.identity.material.upload.dlq";
    public static final String RETRY_HEADER = "x-identity-material-retry-count";
    private static final long RETRY_DELAY_MS = 10000L;

    @Bean
    public DirectExchange identityMaterialExchange() {
        return new DirectExchange(EXCHANGE, true, false);
    }

    @Bean
    public Queue identityMaterialQueue() {
        return QueueBuilder.durable(QUEUE).build();
    }

    @Bean
    public Queue identityMaterialRetryQueue() {
        return QueueBuilder.durable(RETRY_QUEUE)
                .withArgument("x-message-ttl", RETRY_DELAY_MS)
                .withArgument("x-dead-letter-exchange", EXCHANGE)
                .withArgument("x-dead-letter-routing-key", ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue identityMaterialDlqQueue() {
        return QueueBuilder.durable(DLQ).build();
    }

    @Bean
    public Binding identityMaterialBinding(Queue identityMaterialQueue, DirectExchange identityMaterialExchange) {
        return BindingBuilder.bind(identityMaterialQueue).to(identityMaterialExchange).with(ROUTING_KEY);
    }

    @Bean
    public Binding identityMaterialRetryBinding(DirectExchange identityMaterialExchange, Queue identityMaterialRetryQueue) {
        return BindingBuilder.bind(identityMaterialRetryQueue).to(identityMaterialExchange).with(RETRY_ROUTING_KEY);
    }

    @Bean
    public Binding identityMaterialDlqBinding(DirectExchange identityMaterialExchange, Queue identityMaterialDlqQueue) {
        return BindingBuilder.bind(identityMaterialDlqQueue).to(identityMaterialExchange).with(DLQ_ROUTING_KEY);
    }
}
