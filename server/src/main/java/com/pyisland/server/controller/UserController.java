package com.pyisland.server.controller;

import com.pyisland.server.entity.AdminUser;
import com.pyisland.server.security.PasswordPolicy;
import com.pyisland.server.security.UsernamePolicy;
import com.pyisland.server.service.AdminUserService;
import jakarta.servlet.http.HttpServletRequest;
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
@RequestMapping("/v1/admin-users")
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
        AdminUser user = adminUserService.register(request.username(), request.password());
        if (user == null) {
            return ResponseEntity.status(409).body(Map.of(
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
     * @param http HTTP 请求上下文。
     * @return 删除结果。
     */
    @DeleteMapping
    public ResponseEntity<?> deleteUser(@RequestParam String username, HttpServletRequest http) {
        String caller = (String) http.getAttribute("username");
        if (caller != null && caller.equals(username)) {
            return ResponseEntity.status(400).body(Map.of(
                    "code", 400,
                    "message", "不能删除当前登录的管理员"
            ));
        }
        boolean deleted = adminUserService.deleteUser(username);
        if (!deleted) {
            return ResponseEntity.status(404).body(Map.of(
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
     * 更新管理员资料。仅允许修改当前登录管理员自身资料。
     * @param request 更新请求。
     * @param http HTTP 请求上下文。
     * @return 更新结果。
     */
    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(@RequestBody UpdateProfileRequest request, HttpServletRequest http) {
        if (request.username() == null || request.username().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", 400,
                    "message", "用户名不能为空"
            ));
        }
        String caller = (String) http.getAttribute("username");
        if (caller == null || !caller.equals(request.username())) {
            return ResponseEntity.status(403).body(Map.of(
                    "code", 403,
                    "message", "只能修改当前登录管理员的资料"
            ));
        }
        AdminUser user = adminUserService.getByUsername(request.username());
        if (user == null) {
            return ResponseEntity.status(404).body(Map.of(
                    "code", 404,
                    "message", "用户不存在"
            ));
        }
        if (request.password() != null && !request.password().isBlank()) {
            String passwordError = PasswordPolicy.validate(request.password());
            if (passwordError != null) {
                return ResponseEntity.badRequest().body(Map.of(
                        "code", 400,
                        "message", passwordError
                ));
            }
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
