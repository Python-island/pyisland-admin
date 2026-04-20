package com.pyisland.server.auth.service;

import com.pyisland.server.auth.config.EmailVerificationMqConfig;
import com.pyisland.server.auth.mq.EmailCodeDispatchMessage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 邮箱验证码服务。
 */
@Service
public class EmailVerificationService {

    private static final int CODE_LENGTH = 6;
    private static final long CODE_TTL_SECONDS = 5 * 60;
    private static final long SEND_COOLDOWN_SECONDS = 60;
    private static final int MAX_VERIFY_ATTEMPTS = 5;
    private static final int MAX_IP_SENDS_PER_HOUR = 3;
    private static final int MAX_EMAIL_SENDS_PER_HOUR = 3;
    private static final int MAX_EMAIL_SENDS_PER_DAY = 30;

    private final StringRedisTemplate verificationRedisTemplate;
    private final RabbitTemplate rabbitTemplate;
    private final String verifyCodePepper;

    public EmailVerificationService(
            @Qualifier("verificationRedisTemplate") StringRedisTemplate verificationRedisTemplate,
            RabbitTemplate rabbitTemplate,
            @Value("${VERIFY_CODE_PEPPER:pyisland-verify-pepper}") String verifyCodePepper
    ) {
        this.verificationRedisTemplate = verificationRedisTemplate;
        this.rabbitTemplate = rabbitTemplate;
        this.verifyCodePepper = verifyCodePepper;
    }

    /**
     * 发送验证码场景。
     */
    public enum Scene {
        REGISTER,
        LOGIN,
        RESET_PASSWORD,
        CHANGE_EMAIL,
        UNREGISTER
    }

    /**
     * 发送验证码请求参数。
     * @param email 目标邮箱。
     * @param scene 场景。
     * @param clientIp 客户端 IP。
     */
    public record SendCodeCommand(String email, Scene scene, String clientIp) {
    }

    /**
     * 验证验证码请求参数。
     * @param email 目标邮箱。
     * @param scene 场景。
     * @param code 验证码。
     * @param consume 是否一次性消费。
     */
    public record VerifyCodeCommand(String email, Scene scene, String code, boolean consume) {
    }

    /**
     * 发送验证码结果。
     * @param ok 是否成功。
     * @param code 业务码。
     * @param message 结果消息。
     * @param retryAfterSeconds 建议重试等待秒数。
     */
    public record SendCodeResult(boolean ok, int code, String message, long retryAfterSeconds) {
    }

    /**
     * 验证验证码结果。
     * @param ok 是否成功。
     * @param code 业务码。
     * @param message 结果消息。
     */
    public record VerifyCodeResult(boolean ok, int code, String message) {
    }

    /**
     * 发送邮箱验证码。
     */
    public SendCodeResult sendCode(SendCodeCommand command) {
        if (command == null || command.email() == null || command.scene() == null) {
            return new SendCodeResult(false, 400, "请求参数不完整", 0);
        }

        String email = command.email().trim().toLowerCase();
        if (email.isEmpty()) {
            return new SendCodeResult(false, 400, "邮箱不能为空", 0);
        }

        String scene = command.scene().name();
        String cooldownKey = keyCooldown(scene, email);

        try {
            Long cooldownTtl = verificationRedisTemplate.getExpire(cooldownKey);
            if (cooldownTtl != null && cooldownTtl > 0) {
                return new SendCodeResult(false, 429, "发送过于频繁，请稍后再试", cooldownTtl);
            }

            String ip = command.clientIp() == null ? "unknown" : command.clientIp().trim();
            String ipRateKey = keyIpRate(ip);
            Long ipCount = verificationRedisTemplate.opsForValue().increment(ipRateKey);
            if (ipCount != null && ipCount == 1L) {
                verificationRedisTemplate.expire(ipRateKey, Duration.ofHours(1));
            }
            if (ipCount != null && ipCount > MAX_IP_SENDS_PER_HOUR) {
                Long retryAfter = verificationRedisTemplate.getExpire(ipRateKey);
                return new SendCodeResult(false, 429, "请求过于频繁，请稍后再试", retryAfter == null ? 60 : Math.max(1, retryAfter));
            }

            String emailHourlyRateKey = keyEmailHourlyRate(email);
            Long hourlyCount = verificationRedisTemplate.opsForValue().increment(emailHourlyRateKey);
            if (hourlyCount != null && hourlyCount == 1L) {
                verificationRedisTemplate.expire(emailHourlyRateKey, Duration.ofHours(1));
            }
            if (hourlyCount != null && hourlyCount > MAX_EMAIL_SENDS_PER_HOUR) {
                Long retryAfter = verificationRedisTemplate.getExpire(emailHourlyRateKey);
                return new SendCodeResult(false, 429, "当前邮箱发送过于频繁，请稍后再试", retryAfter == null ? 60 : Math.max(1, retryAfter));
            }

            String emailDailyRateKey = keyEmailDailyRate(email);
            Long dailyCount = verificationRedisTemplate.opsForValue().increment(emailDailyRateKey);
            if (dailyCount != null && dailyCount == 1L) {
                long secondsToEndOfDay = secondsToEndOfDayUtc8();
                verificationRedisTemplate.expire(emailDailyRateKey, Duration.ofSeconds(secondsToEndOfDay));
            }
            if (dailyCount != null && dailyCount > MAX_EMAIL_SENDS_PER_DAY) {
                Long retryAfter = verificationRedisTemplate.getExpire(emailDailyRateKey);
                return new SendCodeResult(false, 429, "今日发送次数已达上限", retryAfter == null ? 60 : Math.max(1, retryAfter));
            }

            String plainCode = generateNumericCode(CODE_LENGTH);
            String hashedCode = hashCode(scene, email, plainCode);
            verificationRedisTemplate.opsForValue().set(keyCode(scene, email), hashedCode, Duration.ofSeconds(CODE_TTL_SECONDS));
            verificationRedisTemplate.delete(keyAttempts(scene, email));
            verificationRedisTemplate.opsForValue().set(cooldownKey, "1", Duration.ofSeconds(SEND_COOLDOWN_SECONDS));
            rabbitTemplate.convertAndSend(
                    EmailVerificationMqConfig.EMAIL_CODE_EXCHANGE,
                    EmailVerificationMqConfig.EMAIL_CODE_ROUTING_KEY,
                    new EmailCodeDispatchMessage(
                            UUID.randomUUID().toString(),
                            email,
                            scene,
                            plainCode,
                            System.currentTimeMillis() / 1000
                    )
            );

            return new SendCodeResult(true, 200, "验证码已发送", SEND_COOLDOWN_SECONDS);
        } catch (Exception ex) {
            return new SendCodeResult(false, 503, "验证码服务暂不可用", 0);
        }
    }

    /**
     * 校验邮箱验证码。
     */
    public VerifyCodeResult verifyCode(VerifyCodeCommand command) {
        if (command == null || command.email() == null || command.scene() == null || command.code() == null) {
            return new VerifyCodeResult(false, 400, "请求参数不完整");
        }

        String email = command.email().trim().toLowerCase();
        String scene = command.scene().name();
        String plainCode = command.code().trim();
        if (email.isEmpty() || plainCode.isEmpty()) {
            return new VerifyCodeResult(false, 400, "验证码不能为空");
        }

        try {
            String codeKey = keyCode(scene, email);
            String attemptsKey = keyAttempts(scene, email);

            String storedHash = verificationRedisTemplate.opsForValue().get(codeKey);
            if (storedHash == null || storedHash.isBlank()) {
                return new VerifyCodeResult(false, 400, "验证码不存在或已过期");
            }

            String hashedInput = hashCode(scene, email, plainCode);
            if (!storedHash.equals(hashedInput)) {
                Long attempts = verificationRedisTemplate.opsForValue().increment(attemptsKey);
                Long ttl = verificationRedisTemplate.getExpire(codeKey);
                if (attempts != null && attempts == 1L && ttl != null && ttl > 0) {
                    verificationRedisTemplate.expire(attemptsKey, Duration.ofSeconds(ttl));
                }
                if (attempts != null && attempts >= MAX_VERIFY_ATTEMPTS) {
                    verificationRedisTemplate.delete(codeKey);
                    verificationRedisTemplate.delete(attemptsKey);
                    return new VerifyCodeResult(false, 429, "验证码错误次数过多，请重新获取");
                }
                return new VerifyCodeResult(false, 400, "验证码错误");
            }

            if (command.consume()) {
                verificationRedisTemplate.delete(codeKey);
                verificationRedisTemplate.delete(attemptsKey);
            }

            return new VerifyCodeResult(true, 200, "验证码校验通过");
        } catch (Exception ex) {
            return new VerifyCodeResult(false, 503, "验证码服务暂不可用");
        }
    }

    private String generateNumericCode(int length) {
        int bound = (int) Math.pow(10, length);
        int value = ThreadLocalRandom.current().nextInt(bound);
        return String.format("%0" + length + "d", value);
    }

    private String hashCode(String scene, String email, String plainCode) {
        String raw = verifyCodePepper + "|" + scene + "|" + email + "|" + plainCode;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private long secondsToEndOfDayUtc8() {
        long now = System.currentTimeMillis() / 1000;
        long startOfTomorrow = LocalDate.now(ZoneOffset.ofHours(8)).plusDays(1)
                .atStartOfDay().toEpochSecond(ZoneOffset.ofHours(8));
        long remaining = startOfTomorrow - now;
        return Math.max(60, remaining);
    }

    private String keyCode(String scene, String email) {
        return "verify:code:" + scene + ":" + email;
    }

    private String keyAttempts(String scene, String email) {
        return "verify:attempts:" + scene + ":" + email;
    }

    private String keyCooldown(String scene, String email) {
        return "verify:cooldown:" + scene + ":" + email;
    }

    private String keyIpRate(String ip) {
        return "verify:rate:ip:" + ip;
    }

    private String keyEmailDailyRate(String email) {
        return "verify:rate:email:day:" + email;
    }

    private String keyEmailHourlyRate(String email) {
        return "verify:rate:email:hour:" + email;
    }
}
