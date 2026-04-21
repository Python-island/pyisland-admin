package com.pyisland.server.upload.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 上传接口限流器。
 */
@Component
public class UploadRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(UploadRateLimiter.class);

    public static final int USER_AVATAR_HOURLY_MAX_ATTEMPTS = 3;
    public static final long USER_AVATAR_WINDOW_MS = 60 * 60 * 1000L;

    private final StringRedisTemplate uploadSecurityRedisTemplate;

    public UploadRateLimiter(@Qualifier("uploadSecurityRedisTemplate") StringRedisTemplate uploadSecurityRedisTemplate) {
        this.uploadSecurityRedisTemplate = uploadSecurityRedisTemplate;
    }

    /**
     * 记录一次用户头像上传尝试，并返回限流判定结果。
     * @param ip 客户端 IP。
     * @param account 当前账号。
     * @return 限流结果。
     */
    public Result recordUserAvatarUploadAttempt(String ip, String account) {
        int ipCount = incrementWithinWindow(userAvatarIpKey(ip), USER_AVATAR_WINDOW_MS);
        if (ipCount > USER_AVATAR_HOURLY_MAX_ATTEMPTS) {
            long retryAfterSeconds = remainingTtlSeconds(userAvatarIpKey(ip));
            return Result.blockedByIp(retryAfterSeconds);
        }

        int accountCount = incrementWithinWindow(userAvatarAccountKey(account), USER_AVATAR_WINDOW_MS);
        if (accountCount > USER_AVATAR_HOURLY_MAX_ATTEMPTS) {
            long retryAfterSeconds = remainingTtlSeconds(userAvatarAccountKey(account));
            return Result.blockedByAccount(retryAfterSeconds);
        }

        return Result.allowed();
    }

    private int incrementWithinWindow(String key, long windowMs) {
        try {
            Long count = uploadSecurityRedisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                uploadSecurityRedisTemplate.expire(key, Duration.ofMillis(windowMs));
            }
            return count == null ? 0 : Math.toIntExact(count);
        } catch (Exception ex) {
            log.warn("record upload attempt failed key={}", key, ex);
            return 0;
        }
    }

    private long remainingTtlSeconds(String key) {
        try {
            Long ttl = uploadSecurityRedisTemplate.getExpire(key);
            if (ttl == null || ttl <= 0) {
                return 3600;
            }
            return ttl;
        } catch (Exception ex) {
            log.warn("read upload limiter ttl failed key={}", key, ex);
            return 3600;
        }
    }

    private String userAvatarIpKey(String ip) {
        return "upload:limit:user-avatar:ip:" + ip;
    }

    private String userAvatarAccountKey(String account) {
        return "upload:limit:user-avatar:account:" + account;
    }

    public record Result(boolean blocked, String scope, long retryAfterSeconds) {

        public static Result allowed() {
            return new Result(false, "", 0);
        }

        public static Result blockedByIp(long retryAfterSeconds) {
            return new Result(true, "ip", retryAfterSeconds);
        }

        public static Result blockedByAccount(long retryAfterSeconds) {
            return new Result(true, "account", retryAfterSeconds);
        }
    }
}
