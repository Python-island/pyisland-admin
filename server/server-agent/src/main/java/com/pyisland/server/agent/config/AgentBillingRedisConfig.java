package com.pyisland.server.agent.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;

/**
 * Agent 计费域 Redis 配置（默认 DB12）。
 */
@Configuration
public class AgentBillingRedisConfig {

    @Bean("agentBillingRedisConnectionFactory")
    public LettuceConnectionFactory agentBillingRedisConnectionFactory(
            @Value("${REDIS_HOST:127.0.0.1}") String redisHost,
            @Value("${REDIS_PORT:6379}") int redisPort,
            @Value("${REDIS_PASSWORD:}") String redisPassword,
            @Value("${agent.billing.redis-database:${REDIS_AGENT_BILLING_DATABASE:12}}") int agentBillingRedisDatabase
    ) {
        RedisStandaloneConfiguration standaloneConfiguration = new RedisStandaloneConfiguration();
        standaloneConfiguration.setHostName(redisHost);
        standaloneConfiguration.setPort(redisPort);
        standaloneConfiguration.setDatabase(agentBillingRedisDatabase);
        if (StringUtils.hasText(redisPassword)) {
            standaloneConfiguration.setPassword(redisPassword);
        }
        return new LettuceConnectionFactory(standaloneConfiguration);
    }

    @Bean("agentBillingRedisTemplate")
    public StringRedisTemplate agentBillingRedisTemplate(
            @Qualifier("agentBillingRedisConnectionFactory") LettuceConnectionFactory agentBillingRedisConnectionFactory
    ) {
        StringRedisTemplate template = new StringRedisTemplate();
        template.setConnectionFactory(agentBillingRedisConnectionFactory);
        template.afterPropertiesSet();
        return template;
    }
}
