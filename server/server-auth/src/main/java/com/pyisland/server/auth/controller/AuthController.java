package com.pyisland.server.auth.controller;

import com.pyisland.server.auth.service.EmailVerificationService;
import com.pyisland.server.user.entity.User;
import com.pyisland.server.auth.ratelimit.AuthRateLimiter;
import com.pyisland.server.common.util.ClientIpUtil;
import com.pyisland.server.user.policy.GenderPolicy;
import com.pyisland.server.user.policy.PasswordPolicy;
import com.pyisland.server.user.policy.UsernamePolicy;
import com.pyisland.server.user.service.UserService;
import com.pyisland.server.auth.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 鉴权控制器。保留旧路径兼容：
 * <ul>
 *     <li>POST /auth/admin/login — 管理员登录</li>
 *     <li>POST /auth/admin/register — 管理员注册（首任无需鉴权，后续需 admin JWT）</li>
 *     <li>POST /auth/user/login — 普通用户登录</li>
 *     <li>POST /auth/user/register — 普通用户注册</li>
 * </ul>
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final int LOGIN_STEP_UP_FAILURE_THRESHOLD = 3;

    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final AuthRateLimiter authRateLimiter;
    private final EmailVerificationService emailVerificationService;

    /**
     * 构造鉴权控制器。
     * @param userService 用户服务。
     * @param jwtUtil JWT 工具。
     * @param authRateLimiter 限流器。
     */
    public AuthController(UserService userService,
                          JwtUtil jwtUtil,
                          AuthRateLimiter authRateLimiter,
                          EmailVerificationService emailVerificationService) {
        this.userService = userService;
        this.jwtUtil = jwtUtil;
        this.authRateLimiter = authRateLimiter;
        this.emailVerificationService = emailVerificationService;
    }

    /**
     * 管理员登录。
     * @param request 登录请求。
     * @param http HTTP 请求上下文，用于读取客户端 IP。
     * @return 登录结果。
     */
    @PostMapping("/admin/login")
    public ResponseEntity<?> adminLogin(@RequestBody LoginRequest request, HttpServletRequest http) {
        return doLoginByAccount(request, http, User.ROLE_ADMIN);
    }

    /**
     * 普通用户登录。
     * @param request 登录请求。
     * @param http HTTP 请求上下文。
     * @return 登录结果。
     */
    @PostMapping("/user/login")
    public ResponseEntity<?> userLogin(@RequestBody LoginRequest request, HttpServletRequest http) {
        return doLoginByAccount(request, http, User.ROLE_USER);
    }

    /**
     * 普通用户账户登录（用户名）。
     * @param request 登录请求。
     * @param http HTTP 请求上下文。
     * @return 登录结果。
     */
    @PostMapping("/user/login/account")
    public ResponseEntity<?> userLoginByAccount(@RequestBody LoginRequest request, HttpServletRequest http) {
        return doLoginByAccount(request, http, User.ROLE_USER);
    }

    /**
     * 普通用户邮箱登录。
     * @param request 邮箱登录请求。
     * @param http HTTP 请求上下文。
     * @return 登录结果。
     */
    @PostMapping("/user/login/email")
    public ResponseEntity<?> userLoginByEmail(@RequestBody EmailLoginRequest request, HttpServletRequest http) {
        return doLoginByEmail(request, http, User.ROLE_USER);
    }

    /**
     * 刷新普通用户 JWT（用于同步最新角色 claim）。
     * @param http HTTP 请求上下文。
     * @return 刷新后的 token 与用户信息。
     */
    @PostMapping("/user/token/refresh")
    public ResponseEntity<?> userRefreshToken(HttpServletRequest http) {
        User caller = resolveUserCaller(http);
        if (caller == null) {
            return error(401, "未登录或会话已失效");
        }
        if (Boolean.FALSE.equals(caller.getEnabled())) {
            return error(401, "账号已被禁用");
        }
        String role = caller.getRole();
        if (role == null || role.isBlank()) {
            role = User.ROLE_USER;
        }
        String token = jwtUtil.generateToken(caller.getUsername(), role);
        userService.updateSessionToken(caller.getUsername(), token);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("token", token);
        data.put("username", caller.getUsername());
        data.put("email", caller.getEmail());
        data.put("role", role);
        return okData("刷新成功", data);
    }

    /**
     * 管理员注册。首任管理员无需鉴权；之后需要携带管理员 JWT。
     * 当由 SecurityFilterChain 放行 /auth/** 时，二次管理员注册的鉴权由本方法内部强制校验。
     * @param request 注册请求。
     * @param http HTTP 请求上下文。
     * @return 注册结果。
     */
    @PostMapping("/admin/register")
    public ResponseEntity<?> adminRegister(@RequestBody RegisterRequest request, HttpServletRequest http) {
        String usernameError = UsernamePolicy.validate(request.username());
        if (usernameError != null) {
            return error(400, usernameError);
        }
        String passwordError = PasswordPolicy.validate(request.password());
        if (passwordError != null) {
            return error(400, passwordError);
        }
        int adminCount = userService.countByRole(User.ROLE_ADMIN);
        if (adminCount > 0) {
            String caller = resolveAdminCaller(http);
            if (caller == null) {
                return error(401, "仅限管理员新增管理员");
            }
        }
        String email = normalizeAdminEmail(request.email(), request.username());
        User user = userService.register(request.username(), email, request.password(), User.ROLE_ADMIN);
        if (user == null) {
            return error(409, "用户名或邮箱已存在");
        }
        log.info("admin register success username={}", user.getUsername());
        return ok("添加成功");
    }

    /**
     * 普通用户注册。
     * @param request 注册请求。
     * @param http HTTP 请求上下文。
     * @return 注册结果。
     */
    @PostMapping("/user/register")
    public ResponseEntity<?> userRegister(@RequestBody RegisterRequest request, HttpServletRequest http) {
        String ip = ClientIpUtil.resolve(http);
        if (authRateLimiter.isRegisterBlocked(ip)) {
            return error(429, "注册过于频繁，请稍后再试");
        }
        String usernameError = UsernamePolicy.validate(request.username());
        if (usernameError != null) {
            authRateLimiter.recordRegisterAttempt(ip);
            return error(400, usernameError);
        }
        String passwordError = PasswordPolicy.validate(request.password());
        if (passwordError != null) {
            authRateLimiter.recordRegisterAttempt(ip);
            return error(400, passwordError);
        }
        if (request.email() == null || request.email().isBlank()) {
            authRateLimiter.recordRegisterAttempt(ip);
            return error(400, "邮箱不能为空");
        }
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (!EMAIL_PATTERN.matcher(email).matches() || email.length() > 150) {
            authRateLimiter.recordRegisterAttempt(ip);
            return error(400, "邮箱格式不正确");
        }
        ResponseEntity<?> verifyResult = verifyEmailCodeOrError(email, request.emailCode(), EmailVerificationService.Scene.REGISTER);
        if (verifyResult != null) {
            authRateLimiter.recordRegisterAttempt(ip);
            return verifyResult;
        }
        authRateLimiter.recordRegisterAttempt(ip);
        User user = userService.register(request.username(), email, request.password(), User.ROLE_USER);
        if (user == null) {
            return error(409, "用户名或邮箱已存在");
        }
        String gender = GenderPolicy.normalize(request.gender());
        String genderCustom = GenderPolicy.normalizeCustom(gender, request.genderCustom());
        LocalDate birthday = GenderPolicy.parseBirthday(request.birthday());
        userService.updateExtras(user.getUsername(), gender, genderCustom, birthday);

        String token = jwtUtil.generateToken(user.getUsername(), User.ROLE_USER);
        userService.updateSessionToken(user.getUsername(), token);
        userService.recordDailyActive(user.getUsername(), User.ROLE_USER);
        log.info("user register success username={}", user.getUsername());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("token", token);
        data.put("username", user.getUsername());
        data.put("email", user.getEmail());
        data.put("role", User.ROLE_USER);
        return okData("注册成功", data);
    }

    private ResponseEntity<?> doLoginByAccount(LoginRequest request, HttpServletRequest http, String expectedRole) {
        if (request == null || request.username() == null || request.username().isBlank()
                || request.password() == null || request.password().isBlank()) {
            return error(400, "用户名和密码不能为空");
        }
        String ip = ClientIpUtil.resolve(http);
        String account = request.username().trim();
        String rateKey = "login:account:" + expectedRole + ":" + account + ":" + ip;
        long locked = authRateLimiter.remainingLoginLockSeconds(rateKey);
        if (locked > 0) {
            return error(429, "登录失败次数过多，请 " + locked + " 秒后再试");
        }
        User user = userService.authenticateByUsername(account, request.password());
        if (user == null) {
            authRateLimiter.recordLoginFailure(rateKey);
            return loginFailed();
        }
        if (!isAcceptedLoginRole(expectedRole, user.getRole())) {
            authRateLimiter.recordLoginFailure(rateKey);
            return loginFailed();
        }
        int recentFailures = authRateLimiter.recentLoginFailures(rateKey);
        if (recentFailures >= LOGIN_STEP_UP_FAILURE_THRESHOLD) {
            String email = user.getEmail();
            if (email == null || email.isBlank()) {
                authRateLimiter.recordLoginFailure(rateKey);
                return loginFailed();
            }
            String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
            ResponseEntity<?> verifyResult = verifyEmailCodeOrError(normalizedEmail, request.emailCode(), EmailVerificationService.Scene.LOGIN);
            if (verifyResult != null) {
                authRateLimiter.recordLoginFailure(rateKey);
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("requireEmailVerification", true);
                data.put("maskedEmail", maskEmail(normalizedEmail));
                data.put("verificationEmail", normalizedEmail);
                return errorWithData(428, "当前登录风险较高，请输入邮箱验证码后重试", data);
            }
        }
        authRateLimiter.recordLoginSuccess(rateKey);
        String token = jwtUtil.generateToken(user.getUsername(), user.getRole());
        userService.updateSessionToken(user.getUsername(), token);
        userService.recordDailyActive(user.getUsername(), user.getRole());
        log.info("{} login success username={}", expectedRole, user.getUsername());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("token", token);
        data.put("username", user.getUsername());
        data.put("email", user.getEmail());
        data.put("role", user.getRole());
        return okData("登录成功", data);
    }

    private ResponseEntity<?> doLoginByEmail(EmailLoginRequest request, HttpServletRequest http, String expectedRole) {
        if (request == null || request.email() == null || request.email().isBlank()
                || request.password() == null || request.password().isBlank()) {
            return error(400, "邮箱和密码不能为空");
        }
        String ip = ClientIpUtil.resolve(http);
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        ResponseEntity<?> verifyResult = verifyEmailCodeOrError(email, request.emailCode(), EmailVerificationService.Scene.LOGIN);
        if (verifyResult != null) {
            return verifyResult;
        }
        String rateKey = "login:email:" + expectedRole + ":" + email + ":" + ip;
        long locked = authRateLimiter.remainingLoginLockSeconds(rateKey);
        if (locked > 0) {
            return error(429, "登录失败次数过多，请 " + locked + " 秒后再试");
        }
        User user = userService.authenticateByEmail(email, request.password());
        if (user == null) {
            authRateLimiter.recordLoginFailure(rateKey);
            return loginFailed();
        }
        if (!isAcceptedLoginRole(expectedRole, user.getRole())) {
            authRateLimiter.recordLoginFailure(rateKey);
            return loginFailed();
        }
        authRateLimiter.recordLoginSuccess(rateKey);
        String token = jwtUtil.generateToken(user.getUsername(), user.getRole());
        userService.updateSessionToken(user.getUsername(), token);
        userService.recordDailyActive(user.getUsername(), user.getRole());
        log.info("{} login success username={}", expectedRole, user.getUsername());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("token", token);
        data.put("username", user.getUsername());
        data.put("email", user.getEmail());
        data.put("role", user.getRole());
        return okData("登录成功", data);
    }

    /**
     * 解析 Admin 调用者：仅当请求带有有效的管理员 JWT 时返回用户名。
     * 用于二次管理员注册的鉴权（/auth/** 已经被 SecurityFilterChain 放行）。
     */
    private String resolveAdminCaller(HttpServletRequest http) {
        String authHeader = http.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        String token = authHeader.substring(7).trim();
        if (token.isEmpty() || !jwtUtil.validateToken(token)) {
            return null;
        }
        String role = jwtUtil.getRoleFromToken(token);
        if (!User.ROLE_ADMIN.equals(role)) {
            return null;
        }
        String username = jwtUtil.getUsernameFromToken(token);
        User caller = userService.getByUsername(username);
        if (caller == null || !User.ROLE_ADMIN.equals(caller.getRole())) {
            return null;
        }
        if (caller.getSessionToken() != null && !token.equals(caller.getSessionToken())) {
            return null;
        }
        return username;
    }

    private User resolveUserCaller(HttpServletRequest http) {
        String authHeader = http.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        String token = authHeader.substring(7).trim();
        if (token.isEmpty() || !jwtUtil.validateToken(token)) {
            return null;
        }
        String username;
        try {
            username = jwtUtil.getUsernameFromToken(token);
        } catch (Exception ex) {
            return null;
        }
        User caller = userService.getByUsername(username);
        if (caller == null) {
            return null;
        }
        if (caller.getSessionToken() != null && !token.equals(caller.getSessionToken())) {
            return null;
        }
        return caller;
    }

    private boolean isAcceptedLoginRole(String expectedRole, String actualRole) {
        if (actualRole == null || actualRole.isBlank()) {
            return false;
        }
        if (User.ROLE_USER.equals(expectedRole)) {
            return User.ROLE_USER.equals(actualRole) || User.ROLE_PRO.equals(actualRole);
        }
        return expectedRole.equals(actualRole);
    }

    private String normalizeAdminEmail(String rawEmail, String username) {
        if (rawEmail == null || rawEmail.isBlank()) {
            return username + "@admin.local";
        }
        String email = rawEmail.trim().toLowerCase(Locale.ROOT);
        if (!EMAIL_PATTERN.matcher(email).matches() || email.length() > 150) {
            return username + "@admin.local";
        }
        return email;
    }

    private ResponseEntity<?> verifyEmailCodeOrError(String email, String emailCode, EmailVerificationService.Scene scene) {
        EmailVerificationService.VerifyCodeResult result = emailVerificationService.verifyCode(
                new EmailVerificationService.VerifyCodeCommand(email, scene, emailCode, true)
        );
        if (result.ok()) {
            return null;
        }
        return error(result.code(), result.message());
    }

    private ResponseEntity<Map<String, Object>> ok(String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", 200);
        body.put("message", message);
        return ResponseEntity.ok(body);
    }

    private ResponseEntity<Map<String, Object>> okData(String message, Object data) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", 200);
        body.put("message", message);
        body.put("data", data);
        return ResponseEntity.ok(body);
    }

    private ResponseEntity<Map<String, Object>> error(int code, String message) {
        return errorWithData(code, message, null);
    }

    private ResponseEntity<Map<String, Object>> errorWithData(int code, String message, Object data) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("message", message);
        if (data != null) {
            body.put("data", data);
        }
        int status = switch (code) {
            case 401, 403, 409, 428, 429 -> code;
            default -> 400;
        };
        return ResponseEntity.status(status).body(body);
    }

    private ResponseEntity<Map<String, Object>> loginFailed() {
        return error(401, "登录凭证错误");
    }

    private String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return "";
        }
        int atIndex = email.indexOf('@');
        if (atIndex <= 0 || atIndex >= email.length() - 1) {
            return "***";
        }
        String local = email.substring(0, atIndex);
        String domain = email.substring(atIndex + 1);
        String maskedLocal = local.length() <= 2
                ? local.charAt(0) + "*"
                : local.substring(0, 1) + "***" + local.substring(local.length() - 1);
        return maskedLocal + "@" + domain;
    }

    /**
     * 登录请求体。
     * @param username 用户名。
     * @param password 密码。
     */
    public record LoginRequest(String username, String password, String emailCode) {
    }

    /**
     * 邮箱登录请求体。
     * @param email 邮箱。
     * @param password 密码。
     * @param emailCode 邮箱验证码。
     */
    public record EmailLoginRequest(String email, String password, String emailCode) {
    }

    /**
     * 注册请求体。
     * @param username 用户名。
     * @param email 邮箱（admin 注册可为空，自动填充 {username}@admin.local）。
     * @param password 密码。
     * @param gender 性别（仅用户注册）。
     * @param genderCustom 自定义性别（仅用户注册）。
     * @param birthday 生日（仅用户注册）。
     * @param emailCode 邮箱验证码（仅用户注册）。
     */
    public record RegisterRequest(String username,
                                  String email,
                                  String password,
                                  String gender,
                                  String genderCustom,
                                  String birthday,
                                  String emailCode) {
    }
}
