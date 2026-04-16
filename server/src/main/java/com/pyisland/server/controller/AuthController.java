package com.pyisland.server.controller;

import com.pyisland.server.entity.AdminUser;
import com.pyisland.server.entity.AppUser;
import com.pyisland.server.repository.AdminUserMapper;
import com.pyisland.server.repository.AppUserMapper;
import com.pyisland.server.service.AdminUserService;
import com.pyisland.server.service.AppUserService;
import com.pyisland.server.util.JwtUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * 认证接口控制器。
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");

    private final AdminUserService adminUserService;
    private final AppUserService appUserService;
    private final AdminUserMapper adminUserMapper;
    private final AppUserMapper appUserMapper;
    private final JwtUtil jwtUtil;

    /**
     * 构造认证控制器。
     * @param adminUserService 管理员服务。
     * @param appUserService 普通用户服务。
     * @param adminUserMapper 管理员数据访问接口。
     * @param appUserMapper 普通用户数据访问接口。
     * @param jwtUtil JWT 工具。
     */
    public AuthController(AdminUserService adminUserService,
                          AppUserService appUserService,
                          AdminUserMapper adminUserMapper,
                          AppUserMapper appUserMapper,
                          JwtUtil jwtUtil) {
        this.adminUserService = adminUserService;
        this.appUserService = appUserService;
        this.adminUserMapper = adminUserMapper;
        this.appUserMapper = appUserMapper;
        this.jwtUtil = jwtUtil;
    }

    /**
     * 管理员登录（兼容旧接口）。
     * @param request 登录请求。
     * @return 登录结果。
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AdminLoginRequest request) {
        return adminLogin(request);
    }

    /**
     * 管理员登录。
     * @param request 登录请求。
     * @return 登录结果。
     */
    @PostMapping("/admin/login")
    public ResponseEntity<?> adminLogin(@RequestBody AdminLoginRequest request) {
        if (request.username() == null || request.password() == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", 400,
                    "message", "用户名和密码不能为空"
            ));
        }
        AdminUser user = adminUserService.authenticate(request.username(), request.password());
        if (user == null) {
            return ResponseEntity.ok(Map.of(
                    "code", 401,
                    "message", "管理员用户名或密码错误"
            ));
        }
        String token = jwtUtil.generateToken(user.getUsername(), "admin");
        adminUserMapper.updateSessionToken(user.getUsername(), token);
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
     * 普通用户注册。
     * @param request 注册请求。
     * @return 注册结果。
     */
    @PostMapping("/user/register")
    public ResponseEntity<?> userRegister(@RequestBody UserRegisterRequest request) {
        if (request.username() == null || request.username().isBlank()
                || request.password() == null || request.password().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", 400,
                    "message", "用户名和密码不能为空"
            ));
        }
        if (request.email() == null || request.email().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", 400,
                    "message", "邮箱不能为空"
            ));
        }
        if (!EMAIL_PATTERN.matcher(request.email()).matches()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", 400,
                    "message", "邮箱格式不正确"
            ));
        }
        if (request.password().length() < 6) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", 400,
                    "message", "密码长度不能小于 6 位"
            ));
        }
        AppUser user = appUserService.register(request.username(), request.email(), request.password());
        if (user == null) {
            return ResponseEntity.ok(Map.of(
                    "code", 409,
                    "message", "用户名或邮箱已存在"
            ));
        }
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "注册成功"
        ));
    }

    /**
     * 普通用户登录。
     * @param request 登录请求。
     * @return 登录结果。
     */
    @PostMapping("/user/login")
    public ResponseEntity<?> userLogin(@RequestBody UserLoginRequest request) {
        if (request.username() == null || request.password() == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", 400,
                    "message", "用户名和密码不能为空"
            ));
        }
        AppUser user = appUserService.authenticate(request.username(), request.password());
        if (user == null) {
            return ResponseEntity.ok(Map.of(
                    "code", 401,
                    "message", "用户名或密码错误"
            ));
        }
        String token = jwtUtil.generateToken(user.getUsername(), "user");
        appUserMapper.updateSessionToken(user.getUsername(), token);
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

    /**
     * 管理员登录请求体。
     * @param username 用户名。
     * @param password 密码。
     */
    public record AdminLoginRequest(String username, String password) {
    }

    /**
     * 普通用户注册请求体。
     * @param username 用户名。
     * @param email 邮箱。
     * @param password 密码。
     */
    public record UserRegisterRequest(String username, String email, String password) {
    }

    /**
     * 普通用户登录请求体。
     * @param username 用户名。
     * @param password 密码。
     */
    public record UserLoginRequest(String username, String password) {
    }
}
