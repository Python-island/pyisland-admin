package com.pyisland.server.agent.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Agent 计费异步落库消息队列配置。
 */
@Configuration
@EnableRabbit
public class AgentBillingMqConfig {

    public static final String EXCHANGE = "eisland.agent.billing.exchange";
    public static final String QUEUE = "eisland.agent.billing.deduct.queue";
    public static final String ROUTING_KEY = "eisland.agent.billing.deduct";
    public static final String RETRY_QUEUE = "eisland.agent.billing.deduct.retry.queue";
    public static final String RETRY_ROUTING_KEY = "eisland.agent.billing.deduct.retry";
    public static final String DLQ = "eisland.agent.billing.deduct.dlq";
    public static final String DLQ_ROUTING_KEY = "eisland.agent.billing.deduct.dlq";
    public static final String RETRY_HEADER = "x-agent-billing-retry-count";
    private static final long RETRY_DELAY_MS = 5000L;

    @Bean
    public DirectExchange agentBillingExchange() {
        return new DirectExchange(EXCHANGE, true, false);
    }

    @Bean
    public Queue agentBillingQueue() {
        return QueueBuilder.durable(QUEUE).build();
    }

    @Bean
    public Queue agentBillingRetryQueue() {
        return QueueBuilder.durable(RETRY_QUEUE)
                .withArgument("x-message-ttl", RETRY_DELAY_MS)
                .withArgument("x-dead-letter-exchange", EXCHANGE)
                .withArgument("x-dead-letter-routing-key", ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue agentBillingDlqQueue() {
        return QueueBuilder.durable(DLQ).build();
    }

    @Bean
    public Binding agentBillingBinding(Queue agentBillingQueue, DirectExchange agentBillingExchange) {
        return BindingBuilder.bind(agentBillingQueue).to(agentBillingExchange).with(ROUTING_KEY);
    }

    @Bean
    public Binding agentBillingRetryBinding(DirectExchange agentBillingExchange, Queue agentBillingRetryQueue) {
        return BindingBuilder.bind(agentBillingRetryQueue).to(agentBillingExchange).with(RETRY_ROUTING_KEY);
    }

    @Bean
    public Binding agentBillingDlqBinding(DirectExchange agentBillingExchange, Queue agentBillingDlqQueue) {
        return BindingBuilder.bind(agentBillingDlqQueue).to(agentBillingExchange).with(DLQ_ROUTING_KEY);
    }
}
