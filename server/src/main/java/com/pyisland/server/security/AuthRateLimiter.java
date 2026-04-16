package com.pyisland.server.security;

import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 认证相关接口的滑动窗口限流器。
 * 用于登录、注册等高危接口防止暴力破解与刷号。
 */
@Component
public class AuthRateLimiter {

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

    private final Map<String, Deque<Long>> failures = new ConcurrentHashMap<>();
    private final Map<String, Long> lockedUntil = new ConcurrentHashMap<>();

    /**
     * 判断指定 key 是否已被登录锁定。
     * @param key 账号+IP 组合键。
     * @return 仍处于锁定期返回剩余秒数，未锁定返回 0。
     */
    public long remainingLoginLockSeconds(String key) {
        Long until = lockedUntil.get(key);
        if (until == null) {
            return 0;
        }
        long now = System.currentTimeMillis();
        if (until <= now) {
            lockedUntil.remove(key);
            return 0;
        }
        return Math.max(1, (until - now) / 1000);
    }

    /**
     * 记录一次登录失败，失败达到阈值后会自动进入锁定期。
     * @param key 账号+IP 组合键。
     */
    public synchronized void recordLoginFailure(String key) {
        long now = System.currentTimeMillis();
        Deque<Long> queue = failures.computeIfAbsent(key, k -> new ArrayDeque<>());
        queue.addLast(now);
        cleanup(queue, now - LOGIN_WINDOW_MS);
        if (queue.size() >= LOGIN_MAX_FAILURES) {
            lockedUntil.put(key, now + LOGIN_LOCK_MS);
            queue.clear();
        }
    }

    /**
     * 登录成功后清理失败计数与锁定。
     * @param key 账号+IP 组合键。
     */
    public void recordLoginSuccess(String key) {
        failures.remove(key);
        lockedUntil.remove(key);
    }

    /**
     * 判断注册 IP 是否超出窗口内允许次数。
     * @param ip 客户端 IP。
     * @return 超限返回 true，否则返回 false。
     */
    public synchronized boolean isRegisterBlocked(String ip) {
        long now = System.currentTimeMillis();
        String key = "register:" + ip;
        Deque<Long> queue = failures.computeIfAbsent(key, k -> new ArrayDeque<>());
        cleanup(queue, now - REGISTER_WINDOW_MS);
        return queue.size() >= REGISTER_MAX_ATTEMPTS;
    }

    /**
     * 记录一次注册尝试。
     * @param ip 客户端 IP。
     */
    public synchronized void recordRegisterAttempt(String ip) {
        long now = System.currentTimeMillis();
        String key = "register:" + ip;
        Deque<Long> queue = failures.computeIfAbsent(key, k -> new ArrayDeque<>());
        queue.addLast(now);
        cleanup(queue, now - REGISTER_WINDOW_MS);
    }

    private void cleanup(Deque<Long> queue, long threshold) {
        Iterator<Long> it = queue.iterator();
        while (it.hasNext()) {
            if (it.next() < threshold) {
                it.remove();
            } else {
                break;
            }
        }
    }
}
