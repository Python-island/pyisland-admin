package com.pyisland.server.version.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;

/**
 * 版本布隆过滤器 Redis 配置（默认 DB0）。
 */
@Configuration
public class VersionBloomRedisConfig {

    @Bean("versionBloomRedisConnectionFactory")
    public LettuceConnectionFactory versionBloomRedisConnectionFactory(
            @Value("${REDIS_HOST:127.0.0.1}") String redisHost,
            @Value("${REDIS_PORT:6379}") int redisPort,
            @Value("${REDIS_PASSWORD:}") String redisPassword,
            @Value("${REDIS_VERSION_BLOOM_DATABASE:0}") int versionBloomRedisDatabase
    ) {
        RedisStandaloneConfiguration standaloneConfiguration = new RedisStandaloneConfiguration();
        standaloneConfiguration.setHostName(redisHost);
        standaloneConfiguration.setPort(redisPort);
        standaloneConfiguration.setDatabase(versionBloomRedisDatabase);
        if (StringUtils.hasText(redisPassword)) {
            standaloneConfiguration.setPassword(redisPassword);
        }
        return new LettuceConnectionFactory(standaloneConfiguration);
    }

    @Bean("versionBloomRedisTemplate")
    public StringRedisTemplate versionBloomRedisTemplate(
            @Qualifier("versionBloomRedisConnectionFactory") LettuceConnectionFactory versionBloomRedisConnectionFactory
    ) {
        StringRedisTemplate template = new StringRedisTemplate();
        template.setConnectionFactory(versionBloomRedisConnectionFactory);
        template.afterPropertiesSet();
        return template;
    }
}
