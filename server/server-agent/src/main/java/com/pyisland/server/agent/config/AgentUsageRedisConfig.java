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
 * Agent 用量统计 Redis 配置（默认 DB13）。
 */
@Configuration
public class AgentUsageRedisConfig {

    @Bean("agentUsageRedisConnectionFactory")
    public LettuceConnectionFactory agentUsageRedisConnectionFactory(
            @Value("${REDIS_HOST:127.0.0.1}") String redisHost,
            @Value("${REDIS_PORT:6379}") int redisPort,
            @Value("${REDIS_PASSWORD:}") String redisPassword,
            @Value("${agent.usage.redis-database:${REDIS_AGENT_USAGE_DATABASE:13}}") int agentUsageRedisDatabase
    ) {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(redisHost);
        config.setPort(redisPort);
        config.setDatabase(agentUsageRedisDatabase);
        if (StringUtils.hasText(redisPassword)) {
            config.setPassword(redisPassword);
        }
        return new LettuceConnectionFactory(config);
    }

    @Bean("agentUsageRedisTemplate")
    public StringRedisTemplate agentUsageRedisTemplate(
            @Qualifier("agentUsageRedisConnectionFactory") LettuceConnectionFactory factory
    ) {
        StringRedisTemplate template = new StringRedisTemplate();
        template.setConnectionFactory(factory);
        template.afterPropertiesSet();
        return template;
    }
}
