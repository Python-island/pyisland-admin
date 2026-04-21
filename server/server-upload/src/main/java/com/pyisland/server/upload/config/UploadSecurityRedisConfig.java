package com.pyisland.server.upload.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;

/**
 * 上传域安全策略 Redis 配置。
 */
@Configuration
public class UploadSecurityRedisConfig {

    @Bean("uploadSecurityRedisConnectionFactory")
    public LettuceConnectionFactory uploadSecurityRedisConnectionFactory(
            @Value("${REDIS_HOST:127.0.0.1}") String redisHost,
            @Value("${REDIS_PORT:6379}") int redisPort,
            @Value("${REDIS_PASSWORD:}") String redisPassword,
            @Value("${REDIS_UPLOAD_SECURITY_DATABASE:6}") int uploadSecurityRedisDatabase
    ) {
        RedisStandaloneConfiguration standaloneConfiguration = new RedisStandaloneConfiguration();
        standaloneConfiguration.setHostName(redisHost);
        standaloneConfiguration.setPort(redisPort);
        standaloneConfiguration.setDatabase(uploadSecurityRedisDatabase);
        if (StringUtils.hasText(redisPassword)) {
            standaloneConfiguration.setPassword(redisPassword);
        }
        return new LettuceConnectionFactory(standaloneConfiguration);
    }

    @Bean("uploadSecurityRedisTemplate")
    public StringRedisTemplate uploadSecurityRedisTemplate(
            @Qualifier("uploadSecurityRedisConnectionFactory") LettuceConnectionFactory uploadSecurityRedisConnectionFactory
    ) {
        StringRedisTemplate template = new StringRedisTemplate();
        template.setConnectionFactory(uploadSecurityRedisConnectionFactory);
        template.afterPropertiesSet();
        return template;
    }
}
