package com.pyisland.server.auth.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 邮箱验证码消息队列配置。
 */
@Configuration
@EnableRabbit
public class EmailVerificationMqConfig {

    public static final String EMAIL_CODE_EXCHANGE = "eisland.email.verify.exchange";
    public static final String EMAIL_CODE_QUEUE = "eisland.email.verify.dispatch.queue";
    public static final String EMAIL_CODE_ROUTING_KEY = "eisland.email.verify.dispatch";
    public static final String EMAIL_CODE_RETRY_QUEUE = "eisland.email.verify.retry.queue";
    public static final String EMAIL_CODE_RETRY_ROUTING_KEY = "eisland.email.verify.retry";
    public static final String EMAIL_CODE_DLQ = "eisland.email.verify.dlq";
    public static final String EMAIL_CODE_DLQ_ROUTING_KEY = "eisland.email.verify.dlq";
    public static final String EMAIL_RETRY_HEADER = "x-email-retry-count";

    @Bean
    public DirectExchange emailCodeExchange() {
        return new DirectExchange(EMAIL_CODE_EXCHANGE, true, false);
    }

    @Bean
    public Queue emailCodeQueue() {
        return QueueBuilder.durable(EMAIL_CODE_QUEUE).build();
    }

    @Bean
    public Queue emailCodeRetryQueue(@Value("${email.notify-retry-delay-ms:10000}") long retryDelayMs) {
        return QueueBuilder.durable(EMAIL_CODE_RETRY_QUEUE)
                .withArgument("x-message-ttl", retryDelayMs)
                .withArgument("x-dead-letter-exchange", EMAIL_CODE_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", EMAIL_CODE_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue emailCodeDlqQueue() {
        return QueueBuilder.durable(EMAIL_CODE_DLQ).build();
    }

    @Bean
    public Binding emailCodeBinding(Queue emailCodeQueue, DirectExchange emailCodeExchange) {
        return BindingBuilder.bind(emailCodeQueue).to(emailCodeExchange).with(EMAIL_CODE_ROUTING_KEY);
    }

    @Bean
    public Binding emailCodeRetryBinding(Queue emailCodeRetryQueue, DirectExchange emailCodeExchange) {
        return BindingBuilder.bind(emailCodeRetryQueue).to(emailCodeExchange).with(EMAIL_CODE_RETRY_ROUTING_KEY);
    }

    @Bean
    public Binding emailCodeDlqBinding(Queue emailCodeDlqQueue, DirectExchange emailCodeExchange) {
        return BindingBuilder.bind(emailCodeDlqQueue).to(emailCodeExchange).with(EMAIL_CODE_DLQ_ROUTING_KEY);
    }

    @Bean
    public JacksonJsonMessageConverter rabbitJsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         JacksonJsonMessageConverter rabbitJsonMessageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(rabbitJsonMessageConverter);
        return rabbitTemplate;
    }

    @Bean
    public RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        return new RabbitAdmin(connectionFactory);
    }
}
