package com.pyisland.server.auth.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
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

    @Bean
    public DirectExchange emailCodeExchange() {
        return new DirectExchange(EMAIL_CODE_EXCHANGE, true, false);
    }

    @Bean
    public Queue emailCodeQueue() {
        return new Queue(EMAIL_CODE_QUEUE, true);
    }

    @Bean
    public Binding emailCodeBinding(Queue emailCodeQueue, DirectExchange emailCodeExchange) {
        return BindingBuilder.bind(emailCodeQueue).to(emailCodeExchange).with(EMAIL_CODE_ROUTING_KEY);
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
