package com.pyisland.server.user.controller;

import com.pyisland.server.user.entity.User;
import com.pyisland.server.user.policy.PasswordPolicy;
import com.pyisland.server.user.policy.UsernamePolicy;
import com.pyisland.server.user.service.TotpSecurityService;
import com.pyisland.server.user.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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
import java.util.Locale;
import java.util.Map;

/**
 * 管理员用户控制器（保留旧路径 /v1/admin-users 兼容 admin React 前端）。
 * 由 SecurityFilterChain 强制要求 role=ADMIN 才能访问。
 */
@RestController
@RequestMapping("/v1/admin-users")
public class UserController {

    private final UserService userService;
    private final TotpSecurityService totpSecurityService;

    /**
     * 构造用户控制器。
     * @param userService 用户服务。
     */
    public UserController(UserService userService,
                          TotpSecurityService totpSecurityService) {
        this.userService = userService;
        this.totpSecurityService = totpSecurityService;
    }

    /**
     * 查询管理员列表。
     * @return 管理员列表。
     */
    @GetMapping
    public ResponseEntity<?> listUsers() {
        List<User> users = userService.listByRole(User.ROLE_ADMIN);
        var safeList = users.stream().map(u -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", u.getId());
            m.put("username", u.getUsername());
            m.put("email", u.getEmail());
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
        int count = userService.countByRole(User.ROLE_ADMIN);
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
        String email = normalizeAdminEmail(request.email(), request.username());
        User user = userService.register(request.username(), email, request.password(), User.ROLE_ADMIN);
        if (user == null) {
            return ResponseEntity.status(409).body(Map.of(
                    "code", 409,
                    "message", "用户名或邮箱已存在"
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
     * @param authentication 当前登录管理员。
     * @return 删除结果。
     */
    @DeleteMapping
    public ResponseEntity<?> deleteUser(@RequestParam String username, Authentication authentication) {
        String caller = authentication != null ? authentication.getName() : null;
        if (caller != null && caller.equals(username)) {
            return ResponseEntity.status(400).body(Map.of(
                    "code", 400,
                    "message", "不能删除当前登录的管理员"
            ));
        }
        User target = userService.getByUsername(username);
        if (target == null || !User.ROLE_ADMIN.equals(target.getRole())) {
            return ResponseEntity.status(404).body(Map.of(
                    "code", 404,
                    "message", "管理员不存在"
            ));
        }
        boolean deleted = userService.deleteByUsername(username);
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
        User user = userService.getByUsername(username);
        if (user == null || !User.ROLE_ADMIN.equals(user.getRole())) {
            return ResponseEntity.ok(Map.of(
                    "code", 404,
                    "message", "用户不存在"
            ));
        }
        Map<String, Object> data = new HashMap<>();
        data.put("username", user.getUsername());
        data.put("email", user.getEmail());
        data.put("avatar", userService.getAvatarByUsername(user.getUsername()));
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
     * @param authentication 当前登录管理员。
     * @return 更新结果。
     */
    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(@RequestBody UpdateProfileRequest request, Authentication authentication) {
        if (request.username() == null || request.username().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", 400,
                    "message", "用户名不能为空"
            ));
        }
        String caller = authentication != null ? authentication.getName() : null;
        if (caller == null || !caller.equals(request.username())) {
            return ResponseEntity.status(403).body(Map.of(
                    "code", 403,
                    "message", "只能修改当前登录管理员的资料"
            ));
        }
        User user = userService.getByUsername(request.username());
        if (user == null || !User.ROLE_ADMIN.equals(user.getRole())) {
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
            userService.updateProfile(request.username(), request.password(), request.avatar());
        } else {
            userService.updateAvatar(request.username(), request.avatar());
        }
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "更新成功"
        ));
    }

    /**
     * 轮换指定管理员的 TOTP Seed。
     * @param username 目标管理员用户名。
     * @return 操作结果。
     */
    @PostMapping("/totp-seed/rotate")
    public ResponseEntity<?> rotateTotpSeed(@RequestParam String username) {
        if (username == null || username.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", 400,
                    "message", "用户名不能为空"
            ));
        }
        String targetUsername = username.trim();
        User target = userService.getByUsername(targetUsername);
        if (target == null || !User.ROLE_ADMIN.equals(target.getRole())) {
            return ResponseEntity.status(404).body(Map.of(
                    "code", 404,
                    "message", "管理员不存在"
            ));
        }
        String seed = totpSecurityService.rotateTotpSeedForClient(targetUsername);
        if (seed == null || seed.isBlank()) {
            return ResponseEntity.status(503).body(Map.of(
                    "code", 503,
                    "message", "TOTP 种子轮换失败"
            ));
        }
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "轮换成功"
        ));
    }

    private String normalizeAdminEmail(String rawEmail, String username) {
        if (rawEmail == null || rawEmail.isBlank()) {
            return username + "@admin.local";
        }
        return rawEmail.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 新增管理员请求体。
     * @param username 用户名。
     * @param email 邮箱（可为空，自动填充 {username}@admin.local）。
     * @param password 密码。
     */
    public record AddUserRequest(String username, String email, String password) {
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
