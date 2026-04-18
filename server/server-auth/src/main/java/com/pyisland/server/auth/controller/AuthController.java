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
            return error(401, "用户名或密码错误");
        }
        if (!expectedRole.equals(user.getRole())) {
            authRateLimiter.recordLoginFailure(rateKey);
            return error(403, "无此角色登录权限");
        }
        authRateLimiter.recordLoginSuccess(rateKey);
        String token = jwtUtil.generateToken(user.getUsername(), user.getRole());
        userService.updateSessionToken(user.getUsername(), token);
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
            return error(401, "邮箱或密码错误");
        }
        if (!expectedRole.equals(user.getRole())) {
            authRateLimiter.recordLoginFailure(rateKey);
            return error(403, "无此角色登录权限");
        }
        authRateLimiter.recordLoginSuccess(rateKey);
        String token = jwtUtil.generateToken(user.getUsername(), user.getRole());
        userService.updateSessionToken(user.getUsername(), token);
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
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("message", message);
        return ResponseEntity.status(code == 429 ? 429 : (code == 401 ? 401 : (code == 403 ? 403 : (code == 409 ? 409 : 400)))).body(body);
    }

    /**
     * 登录请求体。
     * @param username 用户名。
     * @param password 密码。
     */
    public record LoginRequest(String username, String password) {
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
