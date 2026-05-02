package com.pyisland.server.agent.service;

import com.pyisland.server.user.entity.User;
import com.pyisland.server.user.service.UserService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Set;

@Service
public class AgentRealtimeSttAuthService {

    private static final Set<String> ALLOWED_ROLES = Set.of(User.ROLE_USER, User.ROLE_PRO, User.ROLE_ADMIN);

    private final SecretKey jwtKey;
    private final UserService userService;

    public AgentRealtimeSttAuthService(@Value("${jwt.secret}") String jwtSecret,
                                       UserService userService) {
        this.jwtKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        this.userService = userService;
    }

    public AuthResult authenticate(String token) {
        if (token == null || token.isBlank()) {
            return AuthResult.fail("未登录，无法启动语音识别");
        }

        final Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(jwtKey)
                    .build()
                    .parseSignedClaims(token.trim())
                    .getPayload();
        } catch (Exception ex) {
            return AuthResult.fail("登录状态失效，请重新登录");
        }

        String username = claims.getSubject();
        if (username == null || username.isBlank()) {
            return AuthResult.fail("无效登录态，请重新登录");
        }

        Object roleClaim = claims.get("role");
        String role = roleClaim instanceof String ? ((String) roleClaim).trim().toLowerCase() : "";
        if (role.startsWith("role_")) {
            role = role.substring("role_".length());
        }
        if (!ALLOWED_ROLES.contains(role)) {
            return AuthResult.fail("当前账号无语音识别权限");
        }

        User user = userService.getByUsername(username.trim());
        if (user == null || Boolean.FALSE.equals(user.getEnabled())) {
            return AuthResult.fail("账号不可用，请联系管理员");
        }

        String sessionToken = user.getSessionToken();
        if (sessionToken == null || sessionToken.isBlank() || !sessionToken.equals(token.trim())) {
            return AuthResult.fail("登录状态已失效，请重新登录");
        }

        return AuthResult.ok(user.getUsername());
    }

    public record AuthResult(boolean success, String username, String message) {
        static AuthResult ok(String username) {
            return new AuthResult(true, username, "");
        }

        static AuthResult fail(String message) {
            return new AuthResult(false, "", message);
        }
    }
}
