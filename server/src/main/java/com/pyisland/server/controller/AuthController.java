package com.pyisland.server.controller;

import com.pyisland.server.entity.AdminUser;
import com.pyisland.server.entity.AppUser;
import com.pyisland.server.repository.AdminUserMapper;
import com.pyisland.server.repository.AppUserMapper;
import com.pyisland.server.security.AuthRateLimiter;
import com.pyisland.server.security.ClientIpUtil;
import com.pyisland.server.security.GenderPolicy;
import com.pyisland.server.security.PasswordPolicy;
import com.pyisland.server.security.UsernamePolicy;
import com.pyisland.server.service.AdminUserService;
import com.pyisland.server.service.AppUserService;
import com.pyisland.server.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 认证接口控制器。
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private static final long FAILURE_DELAY_MS = 600L;

    private final AdminUserService adminUserService;
    private final AppUserService appUserService;
    private final AdminUserMapper adminUserMapper;
    private final AppUserMapper appUserMapper;
    private final JwtUtil jwtUtil;
    private final AuthRateLimiter authRateLimiter;

    /**
     * 构造认证控制器。
     * @param adminUserService 管理员服务。
     * @param appUserService 普通用户服务。
     * @param adminUserMapper 管理员数据访问接口。
     * @param appUserMapper 普通用户数据访问接口。
     * @param jwtUtil JWT 工具。
     * @param authRateLimiter 认证限流器。
     */
    public AuthController(AdminUserService adminUserService,
                          AppUserService appUserService,
                          AdminUserMapper adminUserMapper,
                          AppUserMapper appUserMapper,
                          JwtUtil jwtUtil,
                          AuthRateLimiter authRateLimiter) {
        this.adminUserService = adminUserService;
        this.appUserService = appUserService;
        this.adminUserMapper = adminUserMapper;
        this.appUserMapper = appUserMapper;
        this.jwtUtil = jwtUtil;
        this.authRateLimiter = authRateLimiter;
    }

    /**
     * 管理员登录。
     * @param request 登录请求。
     * @param http HTTP 请求上下文。
     * @return 登录结果。
     */
    @PostMapping("/admin/login")
    public ResponseEntity<?> adminLogin(@RequestBody AdminLoginRequest request, HttpServletRequest http) {
        if (request.username() == null || request.password() == null
                || request.username().isBlank() || request.password().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", 400,
                    "message", "用户名和密码不能为空"
            ));
        }
        String ip = ClientIpUtil.resolve(http);
        String key = buildLoginKey("admin", request.username(), ip);
        long lockSec = authRateLimiter.remainingLoginLockSeconds(key);
        if (lockSec > 0) {
            log.warn("admin login blocked by rate limiter username={} ip={} remainSec={}",
                    request.username(), ip, lockSec);
            return ResponseEntity.status(429).body(Map.of(
                    "code", 429,
                    "message", "登录尝试过多，请在 " + lockSec + " 秒后重试"
            ));
        }
        AdminUser user = adminUserService.authenticate(request.username(), request.password());
        if (user == null) {
            authRateLimiter.recordLoginFailure(key);
            sleepQuietly(FAILURE_DELAY_MS);
            log.info("admin login failed username={} ip={}", request.username(), ip);
            return ResponseEntity.status(401).body(Map.of(
                    "code", 401,
                    "message", "用户名或密码错误"
            ));
        }
        authRateLimiter.recordLoginSuccess(key);
        String token = jwtUtil.generateToken(user.getUsername(), "admin");
        adminUserMapper.updateSessionToken(user.getUsername(), token);
        log.info("admin login success username={} ip={}", user.getUsername(), ip);
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "登录成功",
                "data", Map.of(
                        "token", token,
                        "username", user.getUsername(),
                        "role", "admin"
                )
        ));
    }

    /**
     * 管理员注册。系统已存在管理员时，必须携带管理员 JWT。
     * @param request 注册请求。
     * @param http HTTP 请求上下文。
     * @return 注册结果。
     */
    @PostMapping("/admin/register")
    public ResponseEntity<?> adminRegister(@RequestBody AdminRegisterRequest request, HttpServletRequest http) {
        String ip = ClientIpUtil.resolve(http);

        int adminCount = adminUserService.count();
        if (adminCount > 0) {
            String auth = http.getHeader("Authorization");
            if (auth == null || !auth.startsWith("Bearer ")) {
                log.warn("admin register rejected without token ip={}", ip);
                return ResponseEntity.status(401).body(Map.of(
                        "code", 401,
                        "message", "需要管理员权限才能注册新管理员"
                ));
            }
            String token = auth.substring(7);
            if (!jwtUtil.validateToken(token) || !"admin".equals(jwtUtil.getRoleFromToken(token))) {
                log.warn("admin register rejected with non-admin token ip={}", ip);
                return ResponseEntity.status(403).body(Map.of(
                        "code", 403,
                        "message", "无管理员权限"
                ));
            }
            AdminUser caller = adminUserMapper.selectByUsername(jwtUtil.getUsernameFromToken(token));
            if (caller == null) {
                log.warn("admin register rejected with stale token ip={}", ip);
                return ResponseEntity.status(401).body(Map.of(
                        "code", 401,
                        "message", "登录状态已失效"
                ));
            }
        } else if (authRateLimiter.isRegisterBlocked(ip)) {
            return ResponseEntity.status(429).body(Map.of(
                    "code", 429,
                    "message", "注册尝试过多，请稍后再试"
            ));
        }

        String usernameError = UsernamePolicy.validate(request.username());
        if (usernameError != null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", 400,
                    "message", usernameError
            ));
        }
        String passwordError = PasswordPolicy.validate(request.password());
        if (passwordError != null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", 400,
                    "message", passwordError
            ));
        }

        if (adminCount == 0) {
            authRateLimiter.recordRegisterAttempt(ip);
        }

        AdminUser user = adminUserService.register(request.username(), request.password());
        if (user == null) {
            return ResponseEntity.status(409).body(Map.of(
                    "code", 409,
                    "message", "管理员用户名已存在"
            ));
        }
        log.info("admin register success username={} ip={} bootstrap={}",
                user.getUsername(), ip, adminCount == 0);
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "管理员注册成功"
        ));
    }

    /**
     * 普通用户注册。
     * @param request 注册请求。
     * @param http HTTP 请求上下文。
     * @return 注册结果。
     */
    @PostMapping("/user/register")
    public ResponseEntity<?> userRegister(@RequestBody UserRegisterRequest request, HttpServletRequest http) {
        String ip = ClientIpUtil.resolve(http);
        if (authRateLimiter.isRegisterBlocked(ip)) {
            return ResponseEntity.status(429).body(Map.of(
                    "code", 429,
                    "message", "注册尝试过多，请稍后再试"
            ));
        }

        String usernameError = UsernamePolicy.validate(request.username());
        if (usernameError != null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", 400,
                    "message", usernameError
            ));
        }
        if (request.email() == null || request.email().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", 400,
                    "message", "邮箱不能为空"
            ));
        }
        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
        if (!EMAIL_PATTERN.matcher(normalizedEmail).matches() || normalizedEmail.length() > 150) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", 400,
                    "message", "邮箱格式不正确"
            ));
        }
        String passwordError = PasswordPolicy.validate(request.password());
        if (passwordError != null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", 400,
                    "message", passwordError
            ));
        }

        authRateLimiter.recordRegisterAttempt(ip);

        AppUser user = appUserService.register(request.username(), normalizedEmail, request.password());
        if (user == null) {
            return ResponseEntity.status(409).body(Map.of(
                    "code", 409,
                    "message", "用户名或邮箱已被使用"
            ));
        }
        String gender = GenderPolicy.normalize(request.gender());
        String genderCustom = GenderPolicy.normalizeCustom(gender, request.genderCustom());
        java.time.LocalDate birthday = GenderPolicy.parseBirthday(request.birthday());
        if (!GenderPolicy.DEFAULT.equals(gender) || genderCustom != null || birthday != null) {
            appUserService.updateExtras(user.getUsername(), gender, genderCustom, birthday);
        }
        log.info("user register success username={} ip={}", user.getUsername(), ip);
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "注册成功"
        ));
    }

    /**
     * 普通用户登录。
     * @param request 登录请求。
     * @param http HTTP 请求上下文。
     * @return 登录结果。
     */
    @PostMapping("/user/login")
    public ResponseEntity<?> userLogin(@RequestBody UserLoginRequest request, HttpServletRequest http) {
        if (request.username() == null || request.password() == null
                || request.username().isBlank() || request.password().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", 400,
                    "message", "用户名和密码不能为空"
            ));
        }
        String ip = ClientIpUtil.resolve(http);
        String key = buildLoginKey("user", request.username(), ip);
        long lockSec = authRateLimiter.remainingLoginLockSeconds(key);
        if (lockSec > 0) {
            log.warn("user login blocked by rate limiter username={} ip={} remainSec={}",
                    request.username(), ip, lockSec);
            return ResponseEntity.status(429).body(Map.of(
                    "code", 429,
                    "message", "登录尝试过多，请在 " + lockSec + " 秒后重试"
            ));
        }
        AppUser user = appUserService.authenticate(request.username(), request.password());
        if (user == null) {
            authRateLimiter.recordLoginFailure(key);
            sleepQuietly(FAILURE_DELAY_MS);
            log.info("user login failed username={} ip={}", request.username(), ip);
            return ResponseEntity.status(401).body(Map.of(
                    "code", 401,
                    "message", "用户名或密码错误"
            ));
        }
        authRateLimiter.recordLoginSuccess(key);
        String token = jwtUtil.generateToken(user.getUsername(), "user");
        appUserMapper.updateSessionToken(user.getUsername(), token);
        log.info("user login success username={} ip={}", user.getUsername(), ip);
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "登录成功",
                "data", Map.of(
                        "token", token,
                        "username", user.getUsername(),
                        "email", user.getEmail(),
                        "role", "user"
                )
        ));
    }

    private static String buildLoginKey(String scope, String username, String ip) {
        String safeUsername = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        return "login:" + scope + ":" + safeUsername + ":" + ip;
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 管理员登录请求体。
     * @param username 用户名。
     * @param password 密码。
     */
    public record AdminLoginRequest(String username, String password) {
    }

    /**
     * 管理员注册请求体。
     * @param username 用户名。
     * @param password 密码。
     */
    public record AdminRegisterRequest(String username, String password) {
    }

    /**
     * 普通用户注册请求体。
     * @param username 用户名。
     * @param email 邮箱。
     * @param password 密码。
     * @param gender 性别标识（可选）。
     * @param genderCustom 自定义性别（可选）。
     * @param birthday 生日（可选，ISO yyyy-MM-dd）。
     */
    public record UserRegisterRequest(String username,
                                      String email,
                                      String password,
                                      String gender,
                                      String genderCustom,
                                      String birthday) {
    }

    /**
     * 普通用户登录请求体。
     * @param username 用户名。
     * @param password 密码。
     */
    public record UserLoginRequest(String username, String password) {
    }
}
