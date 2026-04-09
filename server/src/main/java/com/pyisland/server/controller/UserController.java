package com.pyisland.server.controller;

import com.pyisland.server.entity.AdminUser;
import com.pyisland.server.service.AdminUserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/users")
public class UserController {

    private final AdminUserService adminUserService;

    public UserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    public ResponseEntity<?> listUsers() {
        List<AdminUser> users = adminUserService.listAll();
        var safeList = users.stream().map(u -> Map.of(
                "id", u.getId(),
                "username", u.getUsername(),
                "createdAt", u.getCreatedAt() != null ? u.getCreatedAt().toString() : ""
        )).toList();
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "success",
                "data", safeList
        ));
    }

    @GetMapping("/count")
    public ResponseEntity<?> countUsers() {
        int count = adminUserService.count();
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "success",
                "data", count
        ));
    }

    @PostMapping
    public ResponseEntity<?> addUser(@RequestBody AddUserRequest request) {
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
                "message", "添加成功"
        ));
    }

    @DeleteMapping
    public ResponseEntity<?> deleteUser(@RequestParam String username) {
        boolean deleted = adminUserService.deleteUser(username);
        if (!deleted) {
            return ResponseEntity.ok(Map.of(
                    "code", 404,
                    "message", "用户不存在"
            ));
        }
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "删除成功"
        ));
    }

    public record AddUserRequest(String username, String password) {
    }
}
