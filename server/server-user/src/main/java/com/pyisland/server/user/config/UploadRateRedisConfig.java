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
 * 用户域上传限流 Redis 配置（DB7）。
 */
@Configuration
public class UploadRateRedisConfig {

    @Bean("uploadRateRedisConnectionFactory")
    public LettuceConnectionFactory uploadRateRedisConnectionFactory(
            @Value("${REDIS_HOST:127.0.0.1}") String redisHost,
            @Value("${REDIS_PORT:6379}") int redisPort,
            @Value("${REDIS_PASSWORD:}") String redisPassword,
            @Value("${REDIS_UPLOAD_RATE_DATABASE:${REDIS_UPLOAD_SECURITY_DATABASE:7}}") int uploadRateRedisDatabase
    ) {
        RedisStandaloneConfiguration standaloneConfiguration = new RedisStandaloneConfiguration();
        standaloneConfiguration.setHostName(redisHost);
        standaloneConfiguration.setPort(redisPort);
        standaloneConfiguration.setDatabase(uploadRateRedisDatabase);
        if (StringUtils.hasText(redisPassword)) {
            standaloneConfiguration.setPassword(redisPassword);
        }
        return new LettuceConnectionFactory(standaloneConfiguration);
    }

    @Bean("uploadRateRedisTemplate")
    public StringRedisTemplate uploadRateRedisTemplate(
            @Qualifier("uploadRateRedisConnectionFactory") LettuceConnectionFactory uploadRateRedisConnectionFactory
    ) {
        StringRedisTemplate template = new StringRedisTemplate();
        template.setConnectionFactory(uploadRateRedisConnectionFactory);
        template.afterPropertiesSet();
        return template;
    }
}
