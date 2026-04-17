package com.pyisland.server.user.policy;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 统一的密码哈希服务。
 * 新密码使用 BCrypt；验证时兼容历史 SHA-256 哈希，便于平滑迁移。
 */
@Component
public class PasswordHashService {

    private final PasswordEncoder passwordEncoder;

    /**
     * 构造密码哈希服务。
     * @param passwordEncoder BCrypt 编码器。
     */
    public PasswordHashService(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 生成新密码哈希（BCrypt）。
     * @param rawPassword 明文密码。
     * @return BCrypt 哈希结果。
     */
    public String hash(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    /**
     * 校验明文密码与存储哈希是否匹配。
     * 同时支持历史 SHA-256 存储格式。
     * @param rawPassword 明文密码。
     * @param storedHash 存储的哈希。
     * @return 匹配返回 true。
     */
    public boolean matches(String rawPassword, String storedHash) {
        if (rawPassword == null || storedHash == null || storedHash.isBlank()) {
            return false;
        }
        if (isBcrypt(storedHash)) {
            return passwordEncoder.matches(rawPassword, storedHash);
        }
        return constantTimeEquals(sha256Hex(rawPassword), storedHash);
    }

    /**
     * 判断指定哈希是否为 BCrypt 格式。
     * @param storedHash 存储的哈希。
     * @return 是否 BCrypt。
     */
    public boolean isBcrypt(String storedHash) {
        return storedHash != null
                && (storedHash.startsWith("$2a$")
                        || storedHash.startsWith("$2b$")
                        || storedHash.startsWith("$2y$"));
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        if (a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
