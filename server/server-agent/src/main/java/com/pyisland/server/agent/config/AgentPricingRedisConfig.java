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
 * Agent 模型定价缓存 Redis 配置（默认 DB13）。
 */
@Configuration
public class AgentPricingRedisConfig {

    @Bean("agentPricingRedisConnectionFactory")
    public LettuceConnectionFactory agentPricingRedisConnectionFactory(
            @Value("${REDIS_HOST:127.0.0.1}") String redisHost,
            @Value("${REDIS_PORT:6379}") int redisPort,
            @Value("${REDIS_PASSWORD:}") String redisPassword,
            @Value("${agent.pricing.redis-database:${REDIS_AGENT_PRICING_DATABASE:13}}") int agentPricingRedisDatabase
    ) {
        RedisStandaloneConfiguration standaloneConfiguration = new RedisStandaloneConfiguration();
        standaloneConfiguration.setHostName(redisHost);
        standaloneConfiguration.setPort(redisPort);
        standaloneConfiguration.setDatabase(agentPricingRedisDatabase);
        if (StringUtils.hasText(redisPassword)) {
            standaloneConfiguration.setPassword(redisPassword);
        }
        return new LettuceConnectionFactory(standaloneConfiguration);
    }

    @Bean("agentPricingRedisTemplate")
    public StringRedisTemplate agentPricingRedisTemplate(
            @Qualifier("agentPricingRedisConnectionFactory") LettuceConnectionFactory agentPricingRedisConnectionFactory
    ) {
        StringRedisTemplate template = new StringRedisTemplate();
        template.setConnectionFactory(agentPricingRedisConnectionFactory);
        template.afterPropertiesSet();
        return template;
    }
}
