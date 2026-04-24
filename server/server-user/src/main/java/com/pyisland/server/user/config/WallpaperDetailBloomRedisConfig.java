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
 * 壁纸详情布隆过滤器 Redis 配置（默认 DB3）。
 */
@Configuration
public class WallpaperDetailBloomRedisConfig {

    @Bean("wallpaperDetailBloomRedisConnectionFactory")
    public LettuceConnectionFactory wallpaperDetailBloomRedisConnectionFactory(
            @Value("${REDIS_HOST:127.0.0.1}") String redisHost,
            @Value("${REDIS_PORT:6379}") int redisPort,
            @Value("${REDIS_PASSWORD:}") String redisPassword,
            @Value("${REDIS_WALLPAPER_DETAIL_BLOOM_DATABASE:${REDIS_WALLPAPER_DATABASE:3}}") int wallpaperDetailBloomRedisDatabase
    ) {
        RedisStandaloneConfiguration standaloneConfiguration = new RedisStandaloneConfiguration();
        standaloneConfiguration.setHostName(redisHost);
        standaloneConfiguration.setPort(redisPort);
        standaloneConfiguration.setDatabase(wallpaperDetailBloomRedisDatabase);
        if (StringUtils.hasText(redisPassword)) {
            standaloneConfiguration.setPassword(redisPassword);
        }
        return new LettuceConnectionFactory(standaloneConfiguration);
    }

    @Bean("wallpaperDetailBloomRedisTemplate")
    public StringRedisTemplate wallpaperDetailBloomRedisTemplate(
            @Qualifier("wallpaperDetailBloomRedisConnectionFactory") LettuceConnectionFactory wallpaperDetailBloomRedisConnectionFactory
    ) {
        StringRedisTemplate template = new StringRedisTemplate();
        template.setConnectionFactory(wallpaperDetailBloomRedisConnectionFactory);
        template.afterPropertiesSet();
        return template;
    }
}
