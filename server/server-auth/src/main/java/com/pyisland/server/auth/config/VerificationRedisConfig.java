package com.pyisland.server.auth.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;

/**
 * 验证码 Redis 配置
 */
@Configuration
public class VerificationRedisConfig {

    @Bean("verificationRedisConnectionFactory")
    @Primary
    public LettuceConnectionFactory verificationRedisConnectionFactory(
            @Value("${REDIS_HOST:127.0.0.1}") String redisHost,
            @Value("${REDIS_PORT:6379}") int redisPort,
            @Value("${REDIS_PASSWORD:}") String redisPassword,
            @Value("${REDIS_VERIFY_DATABASE:2}") int verifyRedisDatabase
    ) {
        RedisStandaloneConfiguration standaloneConfiguration = new RedisStandaloneConfiguration();
        standaloneConfiguration.setHostName(redisHost);
        standaloneConfiguration.setPort(redisPort);
        standaloneConfiguration.setDatabase(verifyRedisDatabase);
        if (StringUtils.hasText(redisPassword)) {
            standaloneConfiguration.setPassword(redisPassword);
        }
        return new LettuceConnectionFactory(standaloneConfiguration);
    }

    @Bean("verificationRedisTemplate")
    @Primary
    public StringRedisTemplate verificationRedisTemplate(
            @Qualifier("verificationRedisConnectionFactory") LettuceConnectionFactory verificationRedisConnectionFactory
    ) {
        StringRedisTemplate template = new StringRedisTemplate();
        template.setConnectionFactory(verificationRedisConnectionFactory);
        template.afterPropertiesSet();
        return template;
    }

    @Bean("sliderCaptchaRedisConnectionFactory")
    public LettuceConnectionFactory sliderCaptchaRedisConnectionFactory(
            @Value("${REDIS_HOST:127.0.0.1}") String redisHost,
            @Value("${REDIS_PORT:6379}") int redisPort,
            @Value("${REDIS_PASSWORD:}") String redisPassword,
            @Value("${REDIS_SLIDER_CAPTCHA_DATABASE:4}") int sliderCaptchaRedisDatabase
    ) {
        RedisStandaloneConfiguration standaloneConfiguration = new RedisStandaloneConfiguration();
        standaloneConfiguration.setHostName(redisHost);
        standaloneConfiguration.setPort(redisPort);
        standaloneConfiguration.setDatabase(sliderCaptchaRedisDatabase);
        if (StringUtils.hasText(redisPassword)) {
            standaloneConfiguration.setPassword(redisPassword);
        }
        return new LettuceConnectionFactory(standaloneConfiguration);
    }

    @Bean("sliderCaptchaRedisTemplate")
    public StringRedisTemplate sliderCaptchaRedisTemplate(
            @Qualifier("sliderCaptchaRedisConnectionFactory") LettuceConnectionFactory sliderCaptchaRedisConnectionFactory
    ) {
        StringRedisTemplate template = new StringRedisTemplate();
        template.setConnectionFactory(sliderCaptchaRedisConnectionFactory);
        template.afterPropertiesSet();
        return template;
    }
}
