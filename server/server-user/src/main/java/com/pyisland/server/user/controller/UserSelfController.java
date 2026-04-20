package com.pyisland.server.user.controller;

import com.pyisland.server.user.entity.User;
import com.pyisland.server.user.policy.GenderPolicy;
import com.pyisland.server.user.policy.PasswordHashService;
import com.pyisland.server.user.policy.PasswordPolicy;
import com.pyisland.server.upload.service.R2StorageService;
import com.pyisland.server.user.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * 普通用户自助接口。面向已登录用户（role=user / admin）开放，用户只能操作自身资源。
 * 所有路径以 /v1/user 开头，由 Spring Security 统一保护：hasAnyRole("USER","ADMIN")。
 */
@RestController
@RequestMapping("/v1/user")
@PreAuthorize("hasAnyRole('USER','ADMIN')")
public class UserSelfController {

    private static final Logger log = LoggerFactory.getLogger(UserSelfController.class);

    private final UserService userService;
    private final PasswordHashService passwordHashService;
    private final R2StorageService r2StorageService;

    /**
     * 构造用户自助控制器。
     * @param userService 用户服务。
     * @param passwordHashService 密码哈希服务。
     * @param r2StorageService R2 存储服务，用于改写历史头像 URL。
     */
    public UserSelfController(UserService userService,
                              PasswordHashService passwordHashService,
                              R2StorageService r2StorageService) {
        this.userService = userService;
        this.passwordHashService = passwordHashService;
        this.r2StorageService = r2StorageService;
    }

    /**
     * 获取当前登录用户资料。
     * @param authentication 当前安全上下文。
     * @return 资料数据。
     */
    @GetMapping("/profile")
    public ResponseEntity<?> getProfile(Authentication authentication) {
        String caller = callerName(authentication);
        if (caller == null) {
            return ResponseEntity.status(401).body(Map.of("code", 401, "message", "未登录"));
        }
        User user = userService.getByUsername(caller);
        if (user == null) {
            return ResponseEntity.status(404).body(Map.of("code", 404, "message", "用户不存在"));
        }
        userService.recordDailyActive(caller, user.getRole());
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
     * 修改当前登录用户资料。仅支持修改头像、性别、生日。
     * @param request 更新请求体。
     * @param authentication 当前安全上下文。
     * @return 更新结果。
     */
    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(@RequestBody UpdateSelfProfileRequest request,
                                           Authentication authentication) {
        String caller = callerName(authentication);
        if (caller == null) {
            return ResponseEntity.status(401).body(Map.of("code", 401, "message", "未登录"));
        }
        User user = userService.getByUsername(caller);
        if (user == null) {
            return ResponseEntity.status(404).body(Map.of("code", 404, "message", "用户不存在"));
        }
        if (request.avatar() != null) {
            userService.updateAvatar(caller, request.avatar());
        }
        if (request.gender() != null || request.birthday() != null || request.genderCustom() != null) {
            String gender = GenderPolicy.normalize(
                    request.gender() != null ? request.gender() : user.getGender());
            String genderCustom = GenderPolicy.normalizeCustom(gender, request.genderCustom());
            LocalDate birthday = request.birthday() != null
                    ? GenderPolicy.parseBirthday(request.birthday())
                    : user.getBirthday();
            userService.updateExtras(caller, gender, genderCustom, birthday);
        }
        log.info("user self update profile username={}", caller);
        return ResponseEntity.ok(Map.of("code", 200, "message", "更新成功"));
    }

    /**
     * 修改当前登录用户密码。
     * @param request 密码更新请求体。
     * @param authentication 当前安全上下文。
     * @return 更新结果。
     */
    @PostMapping("/profile/password")
    public ResponseEntity<?> updatePassword(@RequestBody UpdateSelfPasswordRequest request,
                                            Authentication authentication) {
        String caller = callerName(authentication);
        if (caller == null) {
            return ResponseEntity.status(401).body(Map.of("code", 401, "message", "未登录"));
        }
        User user = userService.getByUsername(caller);
        if (user == null) {
            return ResponseEntity.status(404).body(Map.of("code", 404, "message", "用户不存在"));
        }
        if (request == null || request.password() == null || request.password().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "密码不能为空"));
        }
        String passwordError = PasswordPolicy.validate(request.password());
        if (passwordError != null) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", passwordError));
        }
        userService.updatePassword(caller, request.password());
        log.info("user self update password username={}", caller);
        return ResponseEntity.ok(Map.of("code", 200, "message", "更新成功"));
    }

    /**
     * 用户登出：清空服务端记录的 session_token，后续该 token 将被判定为过期。
     * @param authentication 当前安全上下文。
     * @return 登出结果。
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logout(Authentication authentication) {
        String caller = callerName(authentication);
        if (caller == null) {
            return ResponseEntity.status(401).body(Map.of("code", 401, "message", "未登录"));
        }
        userService.updateSessionToken(caller, null);
        log.info("user logout username={}", caller);
        return ResponseEntity.ok(Map.of("code", 200, "message", "已退出登录"));
    }

    /**
     * 用户注销账号（自助销号）。需提交当前密码进行二次确认。
     * @param request 注销请求体。
     * @param authentication 当前安全上下文。
     * @return 注销结果。
     */
    @DeleteMapping("/account")
    public ResponseEntity<?> unregister(@RequestBody UnregisterRequest request,
                                        Authentication authentication) {
        String caller = callerName(authentication);
        if (caller == null) {
            return ResponseEntity.status(401).body(Map.of("code", 401, "message", "未登录"));
        }
        User user = userService.getByUsername(caller);
        if (user == null) {
            return ResponseEntity.status(404).body(Map.of("code", 404, "message", "用户不存在"));
        }
        if (request == null || request.password() == null || request.password().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "请输入当前密码以确认注销"));
        }
        if (!passwordHashService.matches(request.password(), user.getPassword())) {
            log.warn("user unregister failed username={} reason=password_mismatch", caller);
            return ResponseEntity.status(401).body(Map.of("code", 401, "message", "密码错误"));
        }
        boolean deleted = userService.deleteByUsername(caller);
        if (!deleted) {
            return ResponseEntity.status(500).body(Map.of("code", 500, "message", "注销失败"));
        }
        log.info("user unregister success username={}", caller);
        return ResponseEntity.ok(Map.of("code", 200, "message", "账号已注销"));
    }

    private String callerName(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return null;
        }
        return authentication.getName();
    }

    /**
     * 自助修改资料请求体。所有字段均为可选。
     * @param avatar 头像地址。
     * @param gender 性别标识。
     * @param genderCustom 自定义性别（仅在 gender=custom 时生效）。
     * @param birthday 生日（yyyy-MM-dd）。
     */
    public record UpdateSelfProfileRequest(String avatar,
                                           String gender,
                                           String genderCustom,
                                           String birthday) {
    }

    /**
     * 自助修改密码请求体。
     * @param password 新密码。
     */
    public record UpdateSelfPasswordRequest(String password) {
    }

    /**
     * 注销账号请求体。
     * @param password 当前密码用于二次确认。
     */
    public record UnregisterRequest(String password) {
    }
}
