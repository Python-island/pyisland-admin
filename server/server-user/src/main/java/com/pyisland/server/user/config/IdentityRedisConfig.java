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
 * 身份认证 Redis 配置（DB2）。
 * 用于发起认证频率限制与实名状态缓存。
 */
@Configuration
public class IdentityRedisConfig {

    @Bean("identityRedisConnectionFactory")
    public LettuceConnectionFactory identityRedisConnectionFactory(
            @Value("${REDIS_HOST:127.0.0.1}") String redisHost,
            @Value("${REDIS_PORT:6379}") int redisPort,
            @Value("${REDIS_PASSWORD:}") String redisPassword,
            @Value("${REDIS_IDENTITY_DATABASE:2}") int redisIdentityDatabase
    ) {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(redisHost);
        config.setPort(redisPort);
        config.setDatabase(redisIdentityDatabase);
        if (StringUtils.hasText(redisPassword)) {
            config.setPassword(redisPassword);
        }
        return new LettuceConnectionFactory(config);
    }

    @Bean("identityRedisTemplate")
    public StringRedisTemplate identityRedisTemplate(
            @Qualifier("identityRedisConnectionFactory") LettuceConnectionFactory identityRedisConnectionFactory
    ) {
        StringRedisTemplate template = new StringRedisTemplate();
        template.setConnectionFactory(identityRedisConnectionFactory);
        template.afterPropertiesSet();
        return template;
    }
}
