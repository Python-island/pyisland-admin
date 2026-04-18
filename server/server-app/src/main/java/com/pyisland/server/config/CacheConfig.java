package com.pyisland.server.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * Redis 缓存配置。
 */
@Configuration
public class CacheConfig {

    private final String redisHost;
    private final int redisPort;
    private final String redisPassword;
    private final int redisDatabase;
    private final int avatarRedisDatabase;

    /**
     * 构造缓存配置。
     * @param redisHost Redis 主机。
     * @param redisPort Redis 端口。
     * @param redisPassword Redis 密码。
     * @param redisDatabase 默认缓存数据库编号。
     * @param avatarRedisDatabase 头像缓存数据库编号。
     */
    public CacheConfig(@Value("${REDIS_HOST:127.0.0.1}") String redisHost,
                       @Value("${REDIS_PORT:6379}") int redisPort,
                       @Value("${REDIS_PASSWORD:}") String redisPassword,
                       @Value("${REDIS_DATABASE:0}") int redisDatabase,
                       @Value("${REDIS_AVATAR_DATABASE:1}") int avatarRedisDatabase) {
        this.redisHost = redisHost;
        this.redisPort = redisPort;
        this.redisPassword = redisPassword;
        this.redisDatabase = redisDatabase;
        this.avatarRedisDatabase = avatarRedisDatabase;
    }

    /**
     * Redis 缓存默认配置。
     * @return 缓存配置。
     */
    @Primary
    @Bean("cacheManager")
    public RedisCacheManager cacheManager() {
        return createCacheManager(redisDatabase);
    }

    /**
     * 头像缓存专用管理器，使用独立 Redis DB。
     * @return 头像缓存管理器。
     */
    @Bean("avatarCacheManager")
    public RedisCacheManager avatarCacheManager() {
        return createCacheManager(avatarRedisDatabase);
    }

    private RedisCacheManager createCacheManager(int database) {
        RedisStandaloneConfiguration standaloneConfiguration = new RedisStandaloneConfiguration();
        standaloneConfiguration.setHostName(redisHost);
        standaloneConfiguration.setPort(redisPort);
        standaloneConfiguration.setDatabase(database);
        if (StringUtils.hasText(redisPassword)) {
            standaloneConfiguration.setPassword(redisPassword);
        }

        LettuceConnectionFactory avatarConnectionFactory = new LettuceConnectionFactory(standaloneConfiguration);
        avatarConnectionFactory.afterPropertiesSet();

        RedisCacheConfiguration cacheConfiguration = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(5))
                .disableCachingNullValues();

        return RedisCacheManager.builder(avatarConnectionFactory)
                .cacheDefaults(cacheConfiguration)
                .build();
    }
}
