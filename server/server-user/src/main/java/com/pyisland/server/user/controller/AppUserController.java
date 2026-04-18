package com.pyisland.server.user.controller;

import com.pyisland.server.user.entity.User;
import com.pyisland.server.user.policy.GenderPolicy;
import com.pyisland.server.user.policy.PasswordPolicy;
import com.pyisland.server.user.policy.UsernamePolicy;
import com.pyisland.server.upload.service.R2StorageService;
import com.pyisland.server.user.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 普通用户管理控制器（保留旧路径 /v1/app-users 兼容 admin React 前端）。
 * 由 SecurityFilterChain 强制要求 role=ADMIN 才能访问。
 */
@RestController
@RequestMapping("/v1/app-users")
public class AppUserController {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final UserService userService;
    private final R2StorageService r2StorageService;

    /**
     * 构造普通用户控制器。
     * @param userService 用户服务。
     * @param r2StorageService R2 存储服务，用于改写历史 URL。
     */
    public AppUserController(UserService userService,
                             R2StorageService r2StorageService) {
        this.userService = userService;
        this.r2StorageService = r2StorageService;
    }

    /**
     * 查询普通用户列表。
     * @return 普通用户列表。
     */
    @GetMapping
    public ResponseEntity<?> listUsers() {
        List<User> users = userService.listByRole(User.ROLE_USER);
        var safeList = users.stream().map(u -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", u.getId());
            m.put("username", u.getUsername());
            m.put("email", u.getEmail());
            m.put("avatar", r2StorageService.rewriteLegacyUrl(u.getAvatar()));
            m.put("gender", u.getGender() != null ? u.getGender() : GenderPolicy.DEFAULT);
            m.put("genderCustom", u.getGenderCustom());
            m.put("birthday", u.getBirthday() != null ? u.getBirthday().toString() : null);
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
     * 查询普通用户数量。
     * @return 普通用户数量。
     */
    @GetMapping("/count")
    public ResponseEntity<?> countUsers() {
        int count = userService.countByRole(User.ROLE_USER);
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "success",
                "data", count
        ));
    }

    /**
     * 新增普通用户。
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
        User user = userService.register(request.username(), normalizedEmail, request.password(), User.ROLE_USER);
        if (user == null) {
            return ResponseEntity.status(409).body(Map.of(
                    "code", 409,
                    "message", "用户名或邮箱已存在"
            ));
        }
        String gender = GenderPolicy.normalize(request.gender());
        String genderCustom = GenderPolicy.normalizeCustom(gender, request.genderCustom());
        LocalDate birthday = GenderPolicy.parseBirthday(request.birthday());
        userService.updateExtras(user.getUsername(), gender, genderCustom, birthday);
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "添加成功"
        ));
    }

    /**
     * 删除普通用户。
     * @param username 用户名。
     * @return 删除结果。
     */
    @DeleteMapping
    public ResponseEntity<?> deleteUser(@RequestParam String username) {
        User target = userService.getByUsername(username);
        if (target == null || !User.ROLE_USER.equals(target.getRole())) {
            return ResponseEntity.status(404).body(Map.of(
                    "code", 404,
                    "message", "普通用户不存在"
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
     * 查询普通用户资料。
     * @param username 用户名。
     * @return 资料信息。
     */
    @GetMapping("/profile")
    public ResponseEntity<?> getProfile(@RequestParam String username) {
        User user = userService.getByUsername(username);
        if (user == null || !User.ROLE_USER.equals(user.getRole())) {
            return ResponseEntity.ok(Map.of(
                    "code", 404,
                    "message", "用户不存在"
            ));
        }
        Map<String, Object> data = new HashMap<>();
        data.put("username", user.getUsername());
        data.put("email", user.getEmail());
        data.put("avatar", r2StorageService.rewriteLegacyUrl(userService.getAvatarByUsername(user.getUsername())));
        data.put("gender", user.getGender() != null ? user.getGender() : GenderPolicy.DEFAULT);
        data.put("genderCustom", user.getGenderCustom());
        data.put("birthday", user.getBirthday() != null ? user.getBirthday().toString() : null);
        data.put("createdAt", user.getCreatedAt() != null ? user.getCreatedAt().toString() : "");
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "success",
                "data", data
        ));
    }

    /**
     * 更新普通用户资料。仅管理员可调用（Security 层强制）。
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
        User user = userService.getByUsername(request.username());
        if (user == null || !User.ROLE_USER.equals(user.getRole())) {
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
        } else if (request.avatar() != null) {
            userService.updateAvatar(request.username(), request.avatar());
        }
        if (request.gender() != null || request.birthday() != null || request.genderCustom() != null) {
            String gender = GenderPolicy.normalize(request.gender() != null ? request.gender() : user.getGender());
            String genderCustom = GenderPolicy.normalizeCustom(gender, request.genderCustom());
            LocalDate birthday = request.birthday() != null
                    ? GenderPolicy.parseBirthday(request.birthday())
                    : user.getBirthday();
            userService.updateExtras(request.username(), gender, genderCustom, birthday);
        }
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "更新成功"
        ));
    }

    /**
     * 新增普通用户请求体。
     * @param username 用户名。
     * @param email 邮箱。
     * @param password 密码。
     * @param gender 性别标识（可选）。
     * @param genderCustom 自定义性别（可选）。
     * @param birthday 生日（可选）。
     */
    public record AddUserRequest(String username,
                                 String email,
                                 String password,
                                 String gender,
                                 String genderCustom,
                                 String birthday) {
    }

    /**
     * 更新普通用户资料请求体。
     * @param username 用户名。
     * @param password 新密码。
     * @param avatar 头像地址。
     * @param gender 性别标识（可选）。
     * @param genderCustom 自定义性别（可选）。
     * @param birthday 生日（可选）。
     */
    public record UpdateProfileRequest(String username,
                                       String password,
                                       String avatar,
                                       String gender,
                                       String genderCustom,
                                       String birthday) {
    }
}
