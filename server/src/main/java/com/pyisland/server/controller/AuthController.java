package com.pyisland.server.controller;

import com.pyisland.server.entity.AdminUser;
import com.pyisland.server.repository.AdminUserMapper;
import com.pyisland.server.service.AdminUserService;
import com.pyisland.server.util.JwtUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 认证接口控制器。
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AdminUserService adminUserService;
    private final AdminUserMapper adminUserMapper;
    private final JwtUtil jwtUtil;

    /**
     * 构造认证控制器。
     * @param adminUserService 管理员服务。
     * @param adminUserMapper 管理员数据访问接口。
     * @param jwtUtil JWT 工具。
     */
    public AuthController(AdminUserService adminUserService, AdminUserMapper adminUserMapper, JwtUtil jwtUtil) {
        this.adminUserService = adminUserService;
        this.adminUserMapper = adminUserMapper;
        this.jwtUtil = jwtUtil;
    }

    /**
     * 管理员登录。
     * @param request 登录请求。
     * @return 登录结果。
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
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
                    "message", "用户名或密码错误"
            ));
        }
        String token = jwtUtil.generateToken(user.getUsername());
        adminUserMapper.updateSessionToken(user.getUsername(), token);
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "登录成功",
                "data", Map.of(
                        "token", token,
                        "username", user.getUsername()
                )
        ));
    }

    /**
     * 登录请求体。
     * @param username 用户名。
     * @param password 密码。
     */
    public record LoginRequest(String username, String password) {
    }
}
