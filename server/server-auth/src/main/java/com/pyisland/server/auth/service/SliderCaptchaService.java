package com.pyisland.server.auth.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.Base64;

/**
 * 滑块验证码服务（腾讯验证码）。
 */
@Service
public class SliderCaptchaService {

    private static final int MAX_PENDING_CHALLENGES_PER_ACCOUNT = 3;
    private static final int MAX_PENDING_CHALLENGES_PER_IP = 5;
    private static final RedisScript<List<Long>> TOKEN_BUCKET_SCRIPT = buildTokenBucketScript();

    private final boolean enabled;
    private final String provider;
    private final int minValue;
    private final int maxValue;
    private final int tolerance;
    private final long challengeTtlSeconds;
    private final int createRateLimitAccount;
    private final int createRateLimitIp;
    private final int verifyRateLimitIp;
    private final int verifyFailLimitAccount;
    private final int verifyFailLimitIp;
    private final long verifyFailWindowSeconds;
    private final long rateLimitWindowSeconds;
    private final StringRedisTemplate sliderCaptchaRedisTemplate;

    public SliderCaptchaService(@Value("${captcha.slider.enabled:false}") boolean enabled,
                                @Value("${captcha.slider.provider:builtin}") String provider,
                                @Value("${captcha.slider.builtin.min-value:0}") int minValue,
                                @Value("${captcha.slider.builtin.max-value:100}") int maxValue,
                                @Value("${captcha.slider.builtin.tolerance:0}") int tolerance,
                                @Value("${captcha.slider.builtin.challenge-ttl-seconds:120}") long challengeTtlSeconds,
                                @Value("${captcha.slider.builtin.rate-limit-account-per-minute:12}") int createRateLimitAccount,
                                @Value("${captcha.slider.builtin.rate-limit-ip-per-minute:24}") int createRateLimitIp,
                                @Value("${captcha.slider.builtin.verify-rate-limit-ip-per-minute:60}") int verifyRateLimitIp,
                                @Value("${captcha.slider.builtin.verify-fail-limit-account:3}") int verifyFailLimitAccount,
                                @Value("${captcha.slider.builtin.verify-fail-limit-ip:3}") int verifyFailLimitIp,
                                @Value("${captcha.slider.builtin.verify-fail-window-seconds:600}") long verifyFailWindowSeconds,
                                @Value("${captcha.slider.builtin.rate-limit-window-seconds:60}") long rateLimitWindowSeconds,
                                @Qualifier("sliderCaptchaRedisTemplate") StringRedisTemplate sliderCaptchaRedisTemplate) {
        this.enabled = enabled;
        this.provider = provider == null ? "builtin" : provider.trim().toLowerCase();
        this.minValue = minValue;
        this.maxValue = Math.max(minValue + 10, maxValue);
        this.tolerance = Math.max(0, tolerance);
        this.challengeTtlSeconds = Math.max(30, challengeTtlSeconds);
        this.createRateLimitAccount = Math.max(1, createRateLimitAccount);
        this.createRateLimitIp = Math.max(1, createRateLimitIp);
        this.verifyRateLimitIp = Math.max(1, verifyRateLimitIp);
        this.verifyFailLimitAccount = Math.max(1, verifyFailLimitAccount);
        this.verifyFailLimitIp = Math.max(1, verifyFailLimitIp);
        this.verifyFailWindowSeconds = Math.max(30, verifyFailWindowSeconds);
        this.rateLimitWindowSeconds = Math.max(10, rateLimitWindowSeconds);
        this.sliderCaptchaRedisTemplate = sliderCaptchaRedisTemplate;
    }

    public CaptchaConfig currentConfig() {
        return new CaptchaConfig(enabled, provider, minValue, maxValue, tolerance, challengeTtlSeconds);
    }

    public CaptchaChallenge createChallenge(String account, String userIp) {
        if (!enabled) {
            return new CaptchaChallenge("", 0, 0, 0, 0);
        }
        if (!"builtin".equals(provider)) {
            throw new IllegalStateException("暂不支持的滑块验证码提供方");
        }
        String normalizedAccount = normalizeAccount(account);
        String normalizedIp = normalizeIp(userIp);
        assertRateLimit(
                keyCreateRateAccount(normalizedAccount),
                createRateLimitAccount,
                "当前账户滑块请求过于频繁，请稍后再试"
        );
        assertRateLimit(
                keyCreateRateIp(normalizedIp),
                createRateLimitIp,
                "当前IP滑块请求过于频繁，请稍后再试"
        );
        cleanupStaleChallenges(keyAccountChallenges(normalizedAccount));
        cleanupStaleChallenges(keyIpChallenges(normalizedIp));

        Long pendingCount = sliderCaptchaRedisTemplate.opsForSet().size(keyAccountChallenges(normalizedAccount));
        if (pendingCount != null && pendingCount >= MAX_PENDING_CHALLENGES_PER_ACCOUNT) {
            throw new TooManyPendingChallengesException("当前账户存在未完成的滑块验证，请完成后再重试");
        }
        Long pendingIpCount = sliderCaptchaRedisTemplate.opsForSet().size(keyIpChallenges(normalizedIp));
        if (pendingIpCount != null && pendingIpCount >= MAX_PENDING_CHALLENGES_PER_IP) {
            throw new TooManyPendingChallengesException("当前IP存在未完成的滑块验证过多，请完成后再重试");
        }

        int target = ThreadLocalRandom.current().nextInt(minValue, maxValue + 1);
        String challengeId = UUID.randomUUID().toString().replace("-", "");
        Duration ttl = Duration.ofSeconds(challengeTtlSeconds);
        sliderCaptchaRedisTemplate.opsForValue().set(
                keyChallenge(challengeId),
                String.valueOf(target),
                ttl
        );
        sliderCaptchaRedisTemplate.opsForValue().set(keyChallengeOwner(challengeId), normalizedAccount, ttl);
        sliderCaptchaRedisTemplate.opsForValue().set(keyChallengeIpOwner(challengeId), normalizedIp, ttl);
        sliderCaptchaRedisTemplate.opsForSet().add(keyAccountChallenges(normalizedAccount), challengeId);
        sliderCaptchaRedisTemplate.opsForSet().add(keyIpChallenges(normalizedIp), challengeId);
        sliderCaptchaRedisTemplate.expire(keyAccountChallenges(normalizedAccount), ttl.plusSeconds(10));
        sliderCaptchaRedisTemplate.expire(keyIpChallenges(normalizedIp), ttl.plusSeconds(10));
        return new CaptchaChallenge(challengeId, minValue, maxValue, target, tolerance);
    }

    public VerifyResult verify(String ticket, String randstr, String userIp) {
        if (!enabled) {
            return VerifyResult.success();
        }
        String normalizedIp = normalizeIp(userIp);
        VerifyResult verifyRateLimitResult = checkVerifyRateLimit(normalizedIp);
        if (!verifyRateLimitResult.ok()) {
            return verifyRateLimitResult;
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
            String targetRaw = sliderCaptchaRedisTemplate.opsForValue().get(key);
            if (targetRaw == null || targetRaw.isBlank()) {
                return VerifyResult.failed(400, "滑块挑战已失效，请重新验证");
            }
            String owner = sliderCaptchaRedisTemplate.opsForValue().get(keyChallengeOwner(challengeId));
            String ownerIp = sliderCaptchaRedisTemplate.opsForValue().get(keyChallengeIpOwner(challengeId));
            sliderCaptchaRedisTemplate.delete(key);
            sliderCaptchaRedisTemplate.delete(keyChallengeOwner(challengeId));
            sliderCaptchaRedisTemplate.delete(keyChallengeIpOwner(challengeId));
            if (owner != null && !owner.isBlank()) {
                sliderCaptchaRedisTemplate.opsForSet().remove(keyAccountChallenges(owner), challengeId);
            }
            if (ownerIp != null && !ownerIp.isBlank()) {
                sliderCaptchaRedisTemplate.opsForSet().remove(keyIpChallenges(ownerIp), challengeId);
            }
            int target = Integer.parseInt(targetRaw.trim());
            if (Math.abs(value - target) > tolerance) {
                VerifyResult failLimitedResult = checkAndRecordVerifyFail(owner, ownerIp, normalizedIp);
                if (!failLimitedResult.ok()) {
                    return failLimitedResult;
                }
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

    private String keyChallengeOwner(String challengeId) {
        return "verify:slider:challenge-owner:" + challengeId;
    }

    private String keyChallengeIpOwner(String challengeId) {
        return "verify:slider:challenge-ip-owner:" + challengeId;
    }

    private String keyAccountChallenges(String account) {
        return "verify:slider:account:challenges:" + encodeAccount(account);
    }

    private String keyIpChallenges(String ip) {
        return "verify:slider:ip:challenges:" + encodeAccount(ip);
    }

    private String keyCreateRateAccount(String account) {
        return "verify:slider:rate:create:account:" + encodeAccount(account);
    }

    private String keyCreateRateIp(String ip) {
        return "verify:slider:rate:create:ip:" + encodeAccount(ip);
    }

    private String keyVerifyRateIp(String ip) {
        return "verify:slider:rate:verify:ip:" + encodeAccount(ip);
    }

    private String keyVerifyFailAccount(String account) {
        return "verify:slider:fail:account:" + encodeAccount(account);
    }

    private String keyVerifyFailIp(String ip) {
        return "verify:slider:fail:ip:" + encodeAccount(ip);
    }

    private void assertRateLimit(String key, int maxAllowed, String message) {
        TokenBucketResult result = consumeToken(key, maxAllowed, rateLimitWindowSeconds);
        if (!result.allowed()) {
            throw new TooManyRequestsException(appendRetryAfterHint(message, result.retryAfterSeconds()), result.retryAfterSeconds());
        }
    }

    private VerifyResult checkVerifyRateLimit(String ip) {
        TokenBucketResult result = consumeToken(keyVerifyRateIp(ip), verifyRateLimitIp, rateLimitWindowSeconds);
        if (!result.allowed()) {
            return VerifyResult.failed(429, appendRetryAfterHint("滑块校验请求过于频繁，请稍后再试", result.retryAfterSeconds()));
        }
        return VerifyResult.success();
    }

    private TokenBucketResult consumeToken(String key, int capacity, long refillWindowSeconds) {
        long nowMs = System.currentTimeMillis();
        long ttlSeconds = Math.max(30, refillWindowSeconds * 2);
        double refillPerMs = (double) capacity / (double) refillWindowSeconds / 1000.0d;
        List<?> response = sliderCaptchaRedisTemplate.execute(
                TOKEN_BUCKET_SCRIPT,
                List.of(key),
                String.valueOf(nowMs),
                String.valueOf(capacity),
                String.valueOf(refillPerMs),
                "1",
                String.valueOf(ttlSeconds)
        );
        if (response == null || response.size() < 3) {
            return new TokenBucketResult(true, 0);
        }
        int allowed = parseLuaNumber(response.get(0));
        long retryAfterMs = Math.max(0L, parseLuaLong(response.get(2)));
        long retryAfterSeconds = (retryAfterMs + 999L) / 1000L;
        return new TokenBucketResult(allowed == 1, retryAfterSeconds);
    }

    private int parseLuaNumber(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private long parseLuaLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private String appendRetryAfterHint(String message, long retryAfterSeconds) {
        if (retryAfterSeconds <= 0) {
            return message;
        }
        return message + "（请 " + retryAfterSeconds + " 秒后重试）";
    }

    private static RedisScript<List<Long>> buildTokenBucketScript() {
        DefaultRedisScript<List<Long>> script = new DefaultRedisScript<>();
        @SuppressWarnings("unchecked")
        Class<List<Long>> listClass = (Class<List<Long>>) (Class<?>) List.class;
        script.setResultType(listClass);
        script.setScriptText("""
                local key = KEYS[1]
                local now_ms = tonumber(ARGV[1])
                local capacity = tonumber(ARGV[2])
                local refill_per_ms = tonumber(ARGV[3])
                local requested = tonumber(ARGV[4])
                local ttl_seconds = tonumber(ARGV[5])

                local state = redis.call('HMGET', key, 'tokens', 'ts')
                local tokens = tonumber(state[1])
                local ts = tonumber(state[2])

                if tokens == nil then
                  tokens = capacity
                end
                if ts == nil then
                  ts = now_ms
                end

                local elapsed = now_ms - ts
                if elapsed < 0 then
                  elapsed = 0
                end

                tokens = math.min(capacity, tokens + elapsed * refill_per_ms)

                local allowed = 0
                local retry_after_ms = 0
                if tokens >= requested then
                  tokens = tokens - requested
                  allowed = 1
                else
                  local deficit = requested - tokens
                  retry_after_ms = math.ceil(deficit / refill_per_ms)
                end

                redis.call('HSET', key, 'tokens', tokens, 'ts', now_ms)
                redis.call('EXPIRE', key, ttl_seconds)
                return { allowed, math.floor(tokens), retry_after_ms }
                """);
        return script;
    }

    private VerifyResult checkAndRecordVerifyFail(String owner, String ownerIp, String fallbackIp) {
        String normalizedOwner = normalizeAccount(owner == null ? "" : owner);
        String normalizedIp = normalizeIp(ownerIp == null || ownerIp.isBlank() ? fallbackIp : ownerIp);

        Long accountFail = sliderCaptchaRedisTemplate.opsForValue().increment(keyVerifyFailAccount(normalizedOwner));
        if (accountFail != null && accountFail == 1L) {
            sliderCaptchaRedisTemplate.expire(keyVerifyFailAccount(normalizedOwner), Duration.ofSeconds(verifyFailWindowSeconds));
        }
        if (accountFail != null && accountFail >= verifyFailLimitAccount) {
            return VerifyResult.failed(429, "滑块输入错误次数过多，请稍后再试");
        }

        Long ipFail = sliderCaptchaRedisTemplate.opsForValue().increment(keyVerifyFailIp(normalizedIp));
        if (ipFail != null && ipFail == 1L) {
            sliderCaptchaRedisTemplate.expire(keyVerifyFailIp(normalizedIp), Duration.ofSeconds(verifyFailWindowSeconds));
        }
        if (ipFail != null && ipFail >= verifyFailLimitIp) {
            return VerifyResult.failed(429, "当前IP滑块输入错误次数过多，请稍后再试");
        }

        return VerifyResult.success();
    }

    private void cleanupStaleChallenges(String challengeSetKey) {
        Set<String> challengeIds = sliderCaptchaRedisTemplate.opsForSet().members(challengeSetKey);
        if (challengeIds == null || challengeIds.isEmpty()) {
            return;
        }
        for (String challengeId : challengeIds) {
            if (challengeId == null || challengeId.isBlank()) {
                sliderCaptchaRedisTemplate.opsForSet().remove(challengeSetKey, challengeId);
                continue;
            }
            Boolean exists = sliderCaptchaRedisTemplate.hasKey(keyChallenge(challengeId));
            if (!Boolean.TRUE.equals(exists)) {
                sliderCaptchaRedisTemplate.opsForSet().remove(challengeSetKey, challengeId);
            }
        }
    }

    private String normalizeAccount(String account) {
        if (account == null) {
            return "";
        }
        return account.trim().toLowerCase();
    }

    private String normalizeIp(String userIp) {
        if (userIp == null || userIp.isBlank()) {
            return "unknown";
        }
        return userIp.trim();
    }

    private String encodeAccount(String account) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(account.getBytes(StandardCharsets.UTF_8));
    }

    public static class TooManyPendingChallengesException extends RuntimeException {
        public TooManyPendingChallengesException(String message) {
            super(message);
        }
    }

    public static class TooManyRequestsException extends RuntimeException {
        private final long retryAfterSeconds;

        public TooManyRequestsException(String message, long retryAfterSeconds) {
            super(message);
            this.retryAfterSeconds = Math.max(0, retryAfterSeconds);
        }

        public long retryAfterSeconds() {
            return retryAfterSeconds;
        }
    }

    private record TokenBucketResult(boolean allowed, long retryAfterSeconds) {
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
