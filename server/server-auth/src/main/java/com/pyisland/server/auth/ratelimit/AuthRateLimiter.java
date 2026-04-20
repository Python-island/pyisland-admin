package com.pyisland.server.auth.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 认证相关接口的滑动窗口限流器。
 * 用于登录、注册等高危接口防止暴力破解与刷号。
 */
@Component
public class AuthRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(AuthRateLimiter.class);

    /**
     * 登录失败允许次数。
     */
    public static final int LOGIN_MAX_FAILURES = 5;

    /**
     * 登录失败窗口（毫秒）。
     */
    public static final long LOGIN_WINDOW_MS = 5 * 60 * 1000L;

    /**
     * 登录失败锁定时长（毫秒）。
     */
    public static final long LOGIN_LOCK_MS = 10 * 60 * 1000L;

    /**
     * 注册允许次数。
     */
    public static final int REGISTER_MAX_ATTEMPTS = 5;

    /**
     * 注册窗口（毫秒）。
     */
    public static final long REGISTER_WINDOW_MS = 60 * 60 * 1000L;

    private final StringRedisTemplate authSecurityRedisTemplate;

    public AuthRateLimiter(@Qualifier("authSecurityRedisTemplate") StringRedisTemplate authSecurityRedisTemplate) {
        this.authSecurityRedisTemplate = authSecurityRedisTemplate;
    }

    /**
     * 判断指定 key 是否已被登录锁定。
     * @param key 账号+IP 组合键。
     * @return 仍处于锁定期返回剩余秒数，未锁定返回 0。
     */
    public long remainingLoginLockSeconds(String key) {
        try {
            String lockValue = authSecurityRedisTemplate.opsForValue().get(loginLockKey(key));
            if (lockValue == null || lockValue.isBlank()) {
                return 0;
            }
            long until = Long.parseLong(lockValue);
            long now = System.currentTimeMillis();
            if (until <= now) {
                authSecurityRedisTemplate.delete(loginLockKey(key));
                return 0;
            }
            return Math.max(1, (until - now) / 1000);
        } catch (Exception ex) {
            log.warn("read login lock from redis failed key={}", key, ex);
            return 0;
        }
    }

    /**
     * 记录一次登录失败，失败达到阈值后会自动进入锁定期。
     * @param key 账号+IP 组合键。
     */
    public void recordLoginFailure(String key) {
        try {
            long now = System.currentTimeMillis();
            String failuresKey = loginFailuresKey(key);
            String member = now + ":" + System.nanoTime();
            authSecurityRedisTemplate.opsForZSet().add(failuresKey, member, now);
            authSecurityRedisTemplate.opsForZSet().removeRangeByScore(failuresKey, 0, now - LOGIN_WINDOW_MS);
            authSecurityRedisTemplate.expire(failuresKey, Duration.ofMillis(LOGIN_WINDOW_MS + LOGIN_LOCK_MS));
            Long failureCount = authSecurityRedisTemplate.opsForZSet().zCard(failuresKey);
            if (failureCount != null && failureCount >= LOGIN_MAX_FAILURES) {
                long until = now + LOGIN_LOCK_MS;
                authSecurityRedisTemplate.opsForValue().set(loginLockKey(key), String.valueOf(until), Duration.ofMillis(LOGIN_LOCK_MS));
                authSecurityRedisTemplate.delete(failuresKey);
            }
        } catch (Exception ex) {
            log.warn("record login failure to redis failed key={}", key, ex);
        }
    }

    /**
     * 获取指定 key 在当前窗口内的登录失败次数。
     * @param key 账号+IP 组合键。
     * @return 窗口内失败次数。
     */
    public int recentLoginFailures(String key) {
        try {
            long now = System.currentTimeMillis();
            String failuresKey = loginFailuresKey(key);
            authSecurityRedisTemplate.opsForZSet().removeRangeByScore(failuresKey, 0, now - LOGIN_WINDOW_MS);
            Long size = authSecurityRedisTemplate.opsForZSet().zCard(failuresKey);
            return size == null ? 0 : Math.toIntExact(size);
        } catch (Exception ex) {
            log.warn("read login failures from redis failed key={}", key, ex);
            return 0;
        }
    }

    /**
     * 登录成功后清理失败计数与锁定。
     * @param key 账号+IP 组合键。
     */
    public void recordLoginSuccess(String key) {
        try {
            authSecurityRedisTemplate.delete(loginFailuresKey(key));
            authSecurityRedisTemplate.delete(loginLockKey(key));
        } catch (Exception ex) {
            log.warn("clear login limiter state failed key={}", key, ex);
        }
    }

    /**
     * 判断注册 IP 是否超出窗口内允许次数。
     * @param ip 客户端 IP。
     * @return 超限返回 true，否则返回 false。
     */
    public boolean isRegisterBlocked(String ip) {
        try {
            long now = System.currentTimeMillis();
            String key = registerAttemptsKey(ip);
            authSecurityRedisTemplate.opsForZSet().removeRangeByScore(key, 0, now - REGISTER_WINDOW_MS);
            Long size = authSecurityRedisTemplate.opsForZSet().zCard(key);
            return size != null && size >= REGISTER_MAX_ATTEMPTS;
        } catch (Exception ex) {
            log.warn("read register limiter state failed ip={}", ip, ex);
            return false;
        }
    }

    /**
     * 记录一次注册尝试。
     * @param ip 客户端 IP。
     */
    public void recordRegisterAttempt(String ip) {
        try {
            long now = System.currentTimeMillis();
            String key = registerAttemptsKey(ip);
            String member = now + ":" + System.nanoTime();
            authSecurityRedisTemplate.opsForZSet().add(key, member, now);
            authSecurityRedisTemplate.opsForZSet().removeRangeByScore(key, 0, now - REGISTER_WINDOW_MS);
            authSecurityRedisTemplate.expire(key, Duration.ofMillis(REGISTER_WINDOW_MS));
        } catch (Exception ex) {
            log.warn("record register attempt failed ip={}", ip, ex);
        }
    }

    private String loginFailuresKey(String key) {
        return "auth:limit:login:failures:" + key;
    }

    private String loginLockKey(String key) {
        return "auth:limit:login:lock:" + key;
    }

    private String registerAttemptsKey(String ip) {
        return "auth:limit:register:attempts:" + ip;
    }
}
