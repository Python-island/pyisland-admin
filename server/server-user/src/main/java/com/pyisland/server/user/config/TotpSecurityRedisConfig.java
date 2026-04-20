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
 * TOTP 安全策略 Redis 配置（DB5）。
 */
@Configuration
public class TotpSecurityRedisConfig {

    @Bean("totpSecurityRedisConnectionFactory")
    public LettuceConnectionFactory totpSecurityRedisConnectionFactory(
            @Value("${REDIS_HOST:127.0.0.1}") String redisHost,
            @Value("${REDIS_PORT:6379}") int redisPort,
            @Value("${REDIS_PASSWORD:}") String redisPassword,
            @Value("${REDIS_TOTP_DATABASE:5}") int redisTotpDatabase
    ) {
        RedisStandaloneConfiguration standaloneConfiguration = new RedisStandaloneConfiguration();
        standaloneConfiguration.setHostName(redisHost);
        standaloneConfiguration.setPort(redisPort);
        standaloneConfiguration.setDatabase(redisTotpDatabase);
        if (StringUtils.hasText(redisPassword)) {
            standaloneConfiguration.setPassword(redisPassword);
        }
        return new LettuceConnectionFactory(standaloneConfiguration);
    }

    @Bean("totpSecurityRedisTemplate")
    public StringRedisTemplate totpSecurityRedisTemplate(
            @Qualifier("totpSecurityRedisConnectionFactory") LettuceConnectionFactory totpSecurityRedisConnectionFactory
    ) {
        StringRedisTemplate template = new StringRedisTemplate();
        template.setConnectionFactory(totpSecurityRedisConnectionFactory);
        template.afterPropertiesSet();
        return template;
    }
}
