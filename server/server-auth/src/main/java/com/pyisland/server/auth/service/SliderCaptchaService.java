package com.pyisland.server.auth.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 滑块验证码服务（腾讯验证码）。
 */
@Service
public class SliderCaptchaService {

    private final boolean enabled;
    private final String provider;
    private final int minValue;
    private final int maxValue;
    private final int tolerance;
    private final long challengeTtlSeconds;
    private final StringRedisTemplate verificationRedisTemplate;

    public SliderCaptchaService(@Value("${captcha.slider.enabled:false}") boolean enabled,
                                @Value("${captcha.slider.provider:builtin}") String provider,
                                @Value("${captcha.slider.builtin.min-value:0}") int minValue,
                                @Value("${captcha.slider.builtin.max-value:100}") int maxValue,
                                @Value("${captcha.slider.builtin.tolerance:3}") int tolerance,
                                @Value("${captcha.slider.builtin.challenge-ttl-seconds:120}") long challengeTtlSeconds,
                                @Qualifier("verificationRedisTemplate") StringRedisTemplate verificationRedisTemplate) {
        this.enabled = enabled;
        this.provider = provider == null ? "builtin" : provider.trim().toLowerCase();
        this.minValue = minValue;
        this.maxValue = Math.max(minValue + 10, maxValue);
        this.tolerance = Math.max(1, tolerance);
        this.challengeTtlSeconds = Math.max(30, challengeTtlSeconds);
        this.verificationRedisTemplate = verificationRedisTemplate;
    }

    public CaptchaConfig currentConfig() {
        return new CaptchaConfig(enabled, provider, minValue, maxValue, tolerance, challengeTtlSeconds);
    }

    public CaptchaChallenge createChallenge() {
        if (!enabled) {
            return new CaptchaChallenge("", 0, 0, 0, 0);
        }
        if (!"builtin".equals(provider)) {
            throw new IllegalStateException("暂不支持的滑块验证码提供方");
        }
        int target = ThreadLocalRandom.current().nextInt(minValue, maxValue + 1);
        String challengeId = UUID.randomUUID().toString().replace("-", "");
        verificationRedisTemplate.opsForValue().set(
                keyChallenge(challengeId),
                String.valueOf(target),
                Duration.ofSeconds(challengeTtlSeconds)
        );
        return new CaptchaChallenge(challengeId, minValue, maxValue, target, tolerance);
    }

    public VerifyResult verify(String ticket, String randstr, String userIp) {
        if (!enabled) {
            return VerifyResult.success();
        }
        if (!"builtin".equals(provider)) {
            return VerifyResult.failed(500, "暂不支持的滑块验证码提供方");
        }
        if (ticket == null || ticket.isBlank() || randstr == null || randstr.isBlank()) {
            return VerifyResult.failed(400, "请先完成滑块验证");
        }
        try {
            String challengeId = ticket.trim();
            int value = Integer.parseInt(randstr.trim());
            String key = keyChallenge(challengeId);
            String targetRaw = verificationRedisTemplate.opsForValue().get(key);
            if (targetRaw == null || targetRaw.isBlank()) {
                return VerifyResult.failed(400, "滑块挑战已失效，请重新验证");
            }
            verificationRedisTemplate.delete(key);
            int target = Integer.parseInt(targetRaw.trim());
            if (Math.abs(value - target) > tolerance) {
                return VerifyResult.failed(400, "滑块验证未通过");
            }
            return VerifyResult.success();
        } catch (Exception ex) {
            return VerifyResult.failed(400, "滑块验证参数非法");
        }
    }

    private String keyChallenge(String challengeId) {
        return "verify:slider:challenge:" + challengeId;
    }

    public record VerifyResult(boolean ok, int code, String message) {
        public static VerifyResult success() {
            return new VerifyResult(true, 200, "ok");
        }

        public static VerifyResult failed(int code, String message) {
            return new VerifyResult(false, code, message);
        }
    }

    public record CaptchaConfig(boolean enabled,
                                String provider,
                                int minValue,
                                int maxValue,
                                int tolerance,
                                long challengeTtlSeconds) {
    }

    public record CaptchaChallenge(String challengeId,
                                   int minValue,
                                   int maxValue,
                                   int targetValue,
                                   int tolerance) {
    }
}
