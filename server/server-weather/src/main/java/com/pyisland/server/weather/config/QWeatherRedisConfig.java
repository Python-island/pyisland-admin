package com.pyisland.server.weather.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;

/**
 * 和风天气缓存 Redis 配置（默认 DB11）。
 */
@Configuration
public class QWeatherRedisConfig {

    @Bean("qweatherRedisConnectionFactory")
    public LettuceConnectionFactory qweatherRedisConnectionFactory(
            @Value("${REDIS_HOST:127.0.0.1}") String redisHost,
            @Value("${REDIS_PORT:6379}") int redisPort,
            @Value("${REDIS_PASSWORD:}") String redisPassword,
            @Value("${REDIS_QWEATHER_DATABASE:11}") int qweatherRedisDatabase
    ) {
        RedisStandaloneConfiguration standaloneConfiguration = new RedisStandaloneConfiguration();
        standaloneConfiguration.setHostName(redisHost);
        standaloneConfiguration.setPort(redisPort);
        standaloneConfiguration.setDatabase(qweatherRedisDatabase);
        if (StringUtils.hasText(redisPassword)) {
            standaloneConfiguration.setPassword(redisPassword);
        }
        return new LettuceConnectionFactory(standaloneConfiguration);
    }

    @Bean("qweatherRedisTemplate")
    public StringRedisTemplate qweatherRedisTemplate(
            @Qualifier("qweatherRedisConnectionFactory") LettuceConnectionFactory qweatherRedisConnectionFactory
    ) {
        StringRedisTemplate template = new StringRedisTemplate();
        template.setConnectionFactory(qweatherRedisConnectionFactory);
        template.afterPropertiesSet();
        return template;
    }
}
