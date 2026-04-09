package com.pyisland.server.controller;

import com.pyisland.server.entity.AdminUser;
import com.pyisland.server.service.AdminUserService;
import com.pyisland.server.util.JwtUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AdminUserService adminUserService;
    private final JwtUtil jwtUtil;

    public AuthController(AdminUserService adminUserService, JwtUtil jwtUtil) {
        this.adminUserService = adminUserService;
        this.jwtUtil = jwtUtil;
    }

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
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "登录成功",
                "data", Map.of(
                        "token", token,
                        "username", user.getUsername()
                )
        ));
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody LoginRequest request) {
        if (request.username() == null || request.username().isBlank()
                || request.password() == null || request.password().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", 400,
                    "message", "用户名和密码不能为空"
            ));
        }
        AdminUser user = adminUserService.register(request.username(), request.password());
        if (user == null) {
            return ResponseEntity.ok(Map.of(
                    "code", 409,
                    "message", "用户名已存在"
            ));
        }
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "注册成功"
        ));
    }

    public record LoginRequest(String username, String password) {
    }
}
