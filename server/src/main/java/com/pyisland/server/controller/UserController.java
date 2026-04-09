package com.pyisland.server.controller;

import com.pyisland.server.entity.AdminUser;
import com.pyisland.server.service.AdminUserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理员用户控制器。
 */
@RestController
@RequestMapping("/v1/users")
public class UserController {

    private final AdminUserService adminUserService;

    /**
     * 构造用户控制器。
     * @param adminUserService 管理员服务。
     */
    public UserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    /**
     * 查询管理员列表。
     * @return 管理员列表。
     */
    @GetMapping
    public ResponseEntity<?> listUsers() {
        List<AdminUser> users = adminUserService.listAll();
        var safeList = users.stream().map(u -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", u.getId());
            m.put("username", u.getUsername());
            m.put("avatar", u.getAvatar());
            m.put("createdAt", u.getCreatedAt() != null ? u.getCreatedAt().toString() : "");
            return m;
        }).toList();
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "success",
                "data", safeList
        ));
    }

    /**
     * 查询管理员数量。
     * @return 管理员数量。
     */
    @GetMapping("/count")
    public ResponseEntity<?> countUsers() {
        int count = adminUserService.count();
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "success",
                "data", count
        ));
    }

    /**
     * 新增管理员。
     * @param request 新增请求。
     * @return 新增结果。
     */
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

    /**
     * 删除管理员。
     * @param username 用户名。
     * @return 删除结果。
     */
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

    /**
     * 查询管理员资料。
     * @param username 用户名。
     * @return 资料信息。
     */
    @GetMapping("/profile")
    public ResponseEntity<?> getProfile(@RequestParam String username) {
        AdminUser user = adminUserService.getByUsername(username);
        if (user == null) {
            return ResponseEntity.ok(Map.of(
                    "code", 404,
                    "message", "用户不存在"
            ));
        }
        Map<String, Object> data = new HashMap<>();
        data.put("username", user.getUsername());
        data.put("avatar", user.getAvatar());
        data.put("createdAt", user.getCreatedAt() != null ? user.getCreatedAt().toString() : "");
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "success",
                "data", data
        ));
    }

    /**
     * 更新管理员资料。
     * @param request 更新请求。
     * @return 更新结果。
     */
    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(@RequestBody UpdateProfileRequest request) {
        if (request.username() == null || request.username().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", 400,
                    "message", "用户名不能为空"
            ));
        }
        AdminUser user = adminUserService.getByUsername(request.username());
        if (user == null) {
            return ResponseEntity.ok(Map.of(
                    "code", 404,
                    "message", "用户不存在"
            ));
        }
        if (request.password() != null && !request.password().isBlank()) {
            adminUserService.updateProfile(request.username(), request.password(), request.avatar());
        } else {
            adminUserService.updateAvatar(request.username(), request.avatar());
        }
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "更新成功"
        ));
    }

    /**
     * 新增管理员请求体。
     * @param username 用户名。
     * @param password 密码。
     */
    public record AddUserRequest(String username, String password) {
    }

    /**
     * 更新管理员资料请求体。
     * @param username 用户名。
     * @param password 新密码。
     * @param avatar 头像地址。
     */
    public record UpdateProfileRequest(String username, String password, String avatar) {
    }
}
