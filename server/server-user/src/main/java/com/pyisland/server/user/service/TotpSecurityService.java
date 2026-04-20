package com.pyisland.server.user.service;

import com.pyisland.server.user.entity.User;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;

/**
 * TOTP 安全策略服务：
 * <ul>
 *     <li>AES-256-GCM 加密保存密钥缓存</li>
 *     <li>失败次数限制</li>
 *     <li>防重放（同一动态码短时间内不可重复）</li>
 *     <li>解密后 Secret 缓存（性能优化）</li>
 *     <li>全局频率限制（按用户 / 按 IP）</li>
 * </ul>
 */
@Service
public class TotpSecurityService {

    public static final int TOTP_DIGITS = 6;
    public static final long TOTP_PERIOD_SECONDS = 30L;
    private static final int TOTP_WINDOW_STEPS = 1;
    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final int TOTP_SECRET_BYTES = 20;

    private static final String KEY_PREFIX = "totp:security:";
    private static final int AES_GCM_IV_LENGTH = 12;
    private static final int AES_GCM_TAG_BITS = 128;

    private final StringRedisTemplate redisTemplate;
    private final UserService userService;
    private final SecretKeySpec aesKeySpec;
    private final SecureRandom secureRandom = new SecureRandom();

    private final int failMaxAttempts;
    private final long failWindowSeconds;
    private final int userRateMax;
    private final int ipRateMax;
    private final long rateWindowSeconds;
    private final long replayProtectSeconds;
    private final long decryptedSecretCacheSeconds;
    private final long encryptedSecretCacheSeconds;

    /**
     * 构造 TOTP 安全服务。
     */
    public TotpSecurityService(
            @Qualifier("totpSecurityRedisTemplate") StringRedisTemplate redisTemplate,
            UserService userService,
            @Value("${TOTP_AES_KEY_BASE64:}") String aesKeyBase64,
            @Value("${TOTP_FAIL_MAX_ATTEMPTS:5}") int failMaxAttempts,
            @Value("${TOTP_FAIL_WINDOW_SECONDS:600}") long failWindowSeconds,
            @Value("${TOTP_RATE_LIMIT_USER_PER_MINUTE:30}") int userRateMax,
            @Value("${TOTP_RATE_LIMIT_IP_PER_MINUTE:60}") int ipRateMax,
            @Value("${TOTP_RATE_WINDOW_SECONDS:60}") long rateWindowSeconds,
            @Value("${TOTP_REPLAY_PROTECT_SECONDS:120}") long replayProtectSeconds,
            @Value("${TOTP_DECRYPTED_SECRET_CACHE_SECONDS:60}") long decryptedSecretCacheSeconds,
            @Value("${TOTP_ENCRYPTED_SECRET_CACHE_SECONDS:86400}") long encryptedSecretCacheSeconds
    ) {
        this.redisTemplate = redisTemplate;
        this.userService = userService;
        this.aesKeySpec = buildAesKey(aesKeyBase64);
        this.failMaxAttempts = Math.max(1, failMaxAttempts);
        this.failWindowSeconds = Math.max(60, failWindowSeconds);
        this.userRateMax = Math.max(1, userRateMax);
        this.ipRateMax = Math.max(1, ipRateMax);
        this.rateWindowSeconds = Math.max(1, rateWindowSeconds);
        this.replayProtectSeconds = Math.max(30, replayProtectSeconds);
        this.decryptedSecretCacheSeconds = Math.max(10, decryptedSecretCacheSeconds);
        this.encryptedSecretCacheSeconds = Math.max(300, encryptedSecretCacheSeconds);
    }

    /**
     * 校验 TOTP 并返回错误消息；通过返回 null。
     */
    public String verifyTotpOrMessage(String usernameRaw,
                                      String clientIpRaw,
                                      String codeRaw) {
        if (aesKeySpec == null) {
            return "TOTP 密钥服务未配置";
        }
        String username = normalize(usernameRaw);
        if (username.isEmpty()) {
            return "TOTP 请求用户无效";
        }
        User user = userService.getByUsername(username);
        if (user == null) {
            return "TOTP 请求用户无效";
        }
        String code = normalizeCode(codeRaw);
        if (code == null) {
            return "TOTP 密令格式错误";
        }
        String clientIp = normalize(clientIpRaw);
        if (isRateLimited(username, clientIp)) {
            return "TOTP 请求过于频繁，请稍后再试";
        }

        long remainingLock = remainingFailLockSeconds(username);
        if (remainingLock > 0) {
            return "TOTP 校验失败次数过多，请 " + remainingLock + " 秒后再试";
        }

        byte[] secret = resolveSecret(user);
        if (secret == null || secret.length == 0) {
            return "TOTP 种子无效";
        }

        long currentCounter = Instant.now().getEpochSecond() / TOTP_PERIOD_SECONDS;
        for (long offset = -TOTP_WINDOW_STEPS; offset <= TOTP_WINDOW_STEPS; offset++) {
            long counter = currentCounter + offset;
            String expected = generateTotp(secret, counter);
            if (expected != null && expected.equals(code)) {
                if (isReplay(username, counter, code)) {
                    return "TOTP 密令已使用，请稍后重试";
                }
                clearFailCount(username);
                return null;
            }
        }

        long failCount = recordFail(username);
        if (failCount >= failMaxAttempts) {
            return "TOTP 校验失败次数过多，请稍后再试";
        }
        return "TOTP 密令错误或已过期";
    }

    /**
     * 获取（或初始化）用户 TOTP Seed（Base32，客户端用于生成动态码）。
     * @param usernameRaw 用户名。
     * @return Base32 Seed；失败返回 null。
     */
    public String getOrCreateTotpSeedForClient(String usernameRaw) {
        if (aesKeySpec == null) {
            return null;
        }
        String username = normalize(usernameRaw);
        if (username.isEmpty()) {
            return null;
        }
        User user = userService.getByUsername(username);
        if (user == null) {
            return null;
        }
        return resolveSeed(user);
    }

    /**
     * 强制轮换用户 TOTP Seed（Base32）。
     * @param usernameRaw 用户名。
     * @return 新 Seed；失败返回 null。
     */
    public String rotateTotpSeedForClient(String usernameRaw) {
        if (aesKeySpec == null) {
            return null;
        }
        String username = normalize(usernameRaw);
        if (username.isEmpty()) {
            return null;
        }
        User user = userService.getByUsername(username);
        if (user == null) {
            return null;
        }
        String seed = generateBase32Seed();
        try {
            String encryptedSeed = encrypt(seed.getBytes(StandardCharsets.UTF_8));
            boolean updated = userService.updateTotpSecret(username, encryptedSeed);
            if (!updated) {
                return null;
            }
            refreshSecretCache(username, seed, encryptedSeed);
            return seed;
        } catch (Exception ex) {
            return null;
        }
    }

    private boolean isRateLimited(String username, String clientIp) {
        try {
            String userKey = key("rate:user:" + username);
            Long userCount = redisTemplate.opsForValue().increment(userKey);
            if (userCount != null && userCount == 1L) {
                redisTemplate.expire(userKey, Duration.ofSeconds(rateWindowSeconds));
            }
            if (userCount != null && userCount > userRateMax) {
                return true;
            }

            if (!clientIp.isEmpty()) {
                String ipKey = key("rate:ip:" + clientIp);
                Long ipCount = redisTemplate.opsForValue().increment(ipKey);
                if (ipCount != null && ipCount == 1L) {
                    redisTemplate.expire(ipKey, Duration.ofSeconds(rateWindowSeconds));
                }
                return ipCount != null && ipCount > ipRateMax;
            }
            return false;
        } catch (Exception ex) {
            return true;
        }
    }

    private long remainingFailLockSeconds(String username) {
        try {
            String failKey = key("fail:" + username);
            String raw = redisTemplate.opsForValue().get(failKey);
            if (raw == null) {
                return 0;
            }
            long count = Long.parseLong(raw);
            if (count < failMaxAttempts) {
                return 0;
            }
            Long ttl = redisTemplate.getExpire(failKey);
            return ttl == null || ttl <= 0 ? failWindowSeconds : ttl;
        } catch (Exception ex) {
            return failWindowSeconds;
        }
    }

    private long recordFail(String username) {
        try {
            String failKey = key("fail:" + username);
            Long value = redisTemplate.opsForValue().increment(failKey);
            if (value != null && value == 1L) {
                redisTemplate.expire(failKey, Duration.ofSeconds(failWindowSeconds));
            }
            return value == null ? failMaxAttempts : value;
        } catch (Exception ex) {
            return failMaxAttempts;
        }
    }

    private void clearFailCount(String username) {
        try {
            redisTemplate.delete(key("fail:" + username));
        } catch (Exception ignored) {
        }
    }

    private boolean isReplay(String username, long counter, String code) {
        try {
            String replayKey = key("replay:" + username + ":" + counter + ":" + code);
            Boolean fresh = redisTemplate.opsForValue().setIfAbsent(replayKey, "1", Duration.ofSeconds(replayProtectSeconds));
            return fresh == null || !fresh;
        } catch (Exception ex) {
            return true;
        }
    }

    private byte[] resolveSecret(User user) {
        String seed = resolveSeed(user);
        if (seed == null || seed.isBlank()) {
            return null;
        }
        return decodeBase32(seed);
    }

    private String resolveSeed(User user) {
        String username = normalize(user.getUsername());
        if (username.isEmpty()) {
            return null;
        }
        try {
            String plainKey = key("secret:plain:" + username);
            String plainCached = redisTemplate.opsForValue().get(plainKey);
            if (plainCached != null && !plainCached.isBlank()) {
                return plainCached;
            }

            String ciphertext = user.getTotpSecretCiphertext();
            String seed;
            if (ciphertext == null || ciphertext.isBlank()) {
                seed = generateBase32Seed();
                String encryptedSeed = encrypt(seed.getBytes(StandardCharsets.UTF_8));
                boolean updated = userService.updateTotpSecret(username, encryptedSeed);
                if (!updated) {
                    return null;
                }
                ciphertext = encryptedSeed;
            } else {
                byte[] plain = decrypt(ciphertext);
                seed = new String(plain, StandardCharsets.UTF_8);
            }

            redisTemplate.opsForValue().set(
                    plainKey,
                    seed,
                    Duration.ofSeconds(decryptedSecretCacheSeconds)
            );
            redisTemplate.opsForValue().set(
                    key("secret:enc:" + username),
                    ciphertext,
                    Duration.ofSeconds(encryptedSecretCacheSeconds)
            );
            return seed;
        } catch (Exception ex) {
            return null;
        }
    }

    private void refreshSecretCache(String username, String seed, String encryptedSeed) {
        try {
            redisTemplate.opsForValue().set(
                    key("secret:plain:" + username),
                    seed,
                    Duration.ofSeconds(decryptedSecretCacheSeconds)
            );
            redisTemplate.opsForValue().set(
                    key("secret:enc:" + username),
                    encryptedSeed,
                    Duration.ofSeconds(encryptedSecretCacheSeconds)
            );
            redisTemplate.delete(key("fail:" + username));
        } catch (Exception ignored) {
        }
    }

    private String generateBase32Seed() {
        byte[] bytes = new byte[TOTP_SECRET_BYTES];
        secureRandom.nextBytes(bytes);
        return encodeBase32(bytes);
    }

    private String encodeBase32(byte[] data) {
        StringBuilder builder = new StringBuilder((data.length * 8 + 4) / 5);
        int current = 0;
        int bits = 0;
        for (byte datum : data) {
            current = (current << 8) | (datum & 0xFF);
            bits += 8;
            while (bits >= 5) {
                int index = (current >> (bits - 5)) & 0x1F;
                builder.append(BASE32_ALPHABET.charAt(index));
                bits -= 5;
            }
        }
        if (bits > 0) {
            int index = (current << (5 - bits)) & 0x1F;
            builder.append(BASE32_ALPHABET.charAt(index));
        }
        return builder.toString();
    }

    private byte[] decodeBase32(String seed) {
        String normalized = seed == null ? "" : seed.trim().replace("=", "").toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return null;
        }
        int current = 0;
        int bits = 0;
        ByteBuffer out = ByteBuffer.allocate((normalized.length() * 5) / 8 + 8);
        for (int i = 0; i < normalized.length(); i++) {
            int idx = BASE32_ALPHABET.indexOf(normalized.charAt(i));
            if (idx < 0) {
                return null;
            }
            current = (current << 5) | idx;
            bits += 5;
            if (bits >= 8) {
                out.put((byte) ((current >> (bits - 8)) & 0xFF));
                bits -= 8;
            }
        }
        byte[] result = new byte[out.position()];
        out.flip();
        out.get(result);
        return result;
    }

    private SecretKeySpec buildAesKey(String aesKeyBase64) {
        if (aesKeyBase64 == null || aesKeyBase64.isBlank()) {
            return null;
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(aesKeyBase64.trim());
            if (decoded.length != 32) {
                return null;
            }
            return new SecretKeySpec(decoded, "AES");
        } catch (Exception ex) {
            return null;
        }
    }

    private String encrypt(byte[] plain) throws Exception {
        byte[] iv = new byte[AES_GCM_IV_LENGTH];
        secureRandom.nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, aesKeySpec, new GCMParameterSpec(AES_GCM_TAG_BITS, iv));
        byte[] encrypted = cipher.doFinal(plain);
        byte[] merged = new byte[iv.length + encrypted.length];
        System.arraycopy(iv, 0, merged, 0, iv.length);
        System.arraycopy(encrypted, 0, merged, iv.length, encrypted.length);
        return Base64.getEncoder().encodeToString(merged);
    }

    private byte[] decrypt(String cipherTextBase64) throws Exception {
        byte[] merged = Base64.getDecoder().decode(cipherTextBase64);
        if (merged.length <= AES_GCM_IV_LENGTH) {
            throw new IllegalStateException("invalid cipher payload");
        }
        byte[] iv = new byte[AES_GCM_IV_LENGTH];
        byte[] encrypted = new byte[merged.length - AES_GCM_IV_LENGTH];
        System.arraycopy(merged, 0, iv, 0, AES_GCM_IV_LENGTH);
        System.arraycopy(merged, AES_GCM_IV_LENGTH, encrypted, 0, encrypted.length);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, aesKeySpec, new GCMParameterSpec(AES_GCM_TAG_BITS, iv));
        return cipher.doFinal(encrypted);
    }

    private String normalize(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase();
    }

    private String normalizeCode(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.length() != TOTP_DIGITS) {
            return null;
        }
        for (int i = 0; i < trimmed.length(); i++) {
            if (!Character.isDigit(trimmed.charAt(i))) {
                return null;
            }
        }
        return trimmed;
    }

    private String generateTotp(byte[] secret, long counter) {
        if (counter < 0) {
            return null;
        }
        try {
            byte[] data = ByteBuffer.allocate(8).putLong(counter).array();
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret, "HmacSHA1"));
            byte[] hash = mac.doFinal(data);
            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);
            int otp = binary % (int) Math.pow(10, TOTP_DIGITS);
            return String.format("%0" + TOTP_DIGITS + "d", otp);
        } catch (Exception ex) {
            return null;
        }
    }

    private String key(String suffix) {
        return KEY_PREFIX + suffix;
    }
}
