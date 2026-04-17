package com.pyisland.server.auth.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具类。
 */
@Component
public class JwtUtil {

    private final SecretKey key;
    private final long expiration;

    /**
     * 构造 JWT 工具。
     * @param secret 签名密钥。
     * @param expiration 过期时长（毫秒）。
     */
    public JwtUtil(@Value("${jwt.secret}") String secret,
                   @Value("${jwt.expiration}") long expiration) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiration = expiration;
    }

    /**
     * 生成 JWT token。
     * @param username 用户名。
     * @return token 字符串。
     */
    public String generateToken(String username) {
        return generateToken(username, "admin");
    }

    /**
     * 生成带角色信息的 JWT token。
     * @param username 用户名。
     * @param role 角色。
     * @return token 字符串。
     */
    public String generateToken(String username, String role) {
        return Jwts.builder()
                .subject(username)
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(key)
                .compact();
    }

    /**
     * 从 token 提取用户名。
     * @param token token 字符串。
     * @return 用户名。
     */
    public String getUsernameFromToken(String token) {
        return parseClaims(token).getSubject();
    }

    /**
     * 从 token 提取角色。
     * @param token token 字符串。
     * @return 角色，缺失时默认 admin。
     */
    public String getRoleFromToken(String token) {
        Claims claims = parseClaims(token);
        Object role = claims.get("role");
        if (role instanceof String roleStr && !roleStr.isBlank()) {
            return roleStr;
        }
        return "admin";
    }

    /**
     * 校验 token 是否有效。
     * @param token token 字符串。
     * @return 是否有效。
     */
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 解析 token 并返回声明信息。
     * @param token token 字符串。
     * @return 声明对象。
     */
    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
