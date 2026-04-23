package com.pyisland.server.user.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;

/**
 * 用户封禁布隆过滤器 Redis 配置（默认 DB9）。
 */
@Configuration
public class UserBanRedisConfig {

    @Bean("userBanRedisConnectionFactory")
    public LettuceConnectionFactory userBanRedisConnectionFactory(
            @Value("${REDIS_HOST:127.0.0.1}") String redisHost,
            @Value("${REDIS_PORT:6379}") int redisPort,
            @Value("${REDIS_PASSWORD:}") String redisPassword,
            @Value("${REDIS_USER_BAN_DATABASE:9}") int userBanRedisDatabase
    ) {
        RedisStandaloneConfiguration standaloneConfiguration = new RedisStandaloneConfiguration();
        standaloneConfiguration.setHostName(redisHost);
        standaloneConfiguration.setPort(redisPort);
        standaloneConfiguration.setDatabase(userBanRedisDatabase);
        if (StringUtils.hasText(redisPassword)) {
            standaloneConfiguration.setPassword(redisPassword);
        }
        return new LettuceConnectionFactory(standaloneConfiguration);
    }

    @Bean("userBanRedisTemplate")
    public StringRedisTemplate userBanRedisTemplate(
            @Qualifier("userBanRedisConnectionFactory") LettuceConnectionFactory userBanRedisConnectionFactory
    ) {
        StringRedisTemplate template = new StringRedisTemplate();
        template.setConnectionFactory(userBanRedisConnectionFactory);
        template.afterPropertiesSet();
        return template;
    }
}
