package com.pyisland.server.user.controller;

import com.pyisland.server.user.entity.User;
import com.pyisland.server.user.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理面板：统一用户管理接口。
 * 合表后提供 /v1/admin/users 下的全量用户列表、角色变更与启用禁用。
 * 老前端仍可继续使用 /v1/admin-users 与 /v1/app-users 分别查询 admin / user。
 */
@RestController
@RequestMapping("/v1/admin/users")
public class UserAdminController {

    private static final Logger log = LoggerFactory.getLogger(UserAdminController.class);

    private final UserService userService;

    /**
     * 构造管理员端用户管理控制器。
     * @param userService 用户服务。
     */
    public UserAdminController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 查询全量用户列表，可按 role 过滤。
     * @param role 可选 admin / user / 空。
     * @return 用户列表。
     */
    @GetMapping
    public ResponseEntity<?> list(@RequestParam(required = false) String role) {
        String normalizedRole = (role == null || role.isBlank()) ? null : role.trim().toLowerCase();
        List<User> users = userService.listByRole(normalizedRole);
        var safeList = users.stream().map(this::toListItem).toList();
        return ResponseEntity.ok(Map.of("code", 200, "message", "success", "data", safeList));
    }

    /**
     * 变更用户角色。禁止把自己降级为普通用户。
     * @param request 变更请求。
     * @param authentication 当前登录管理员。
     * @return 操作结果。
     */
    @PutMapping("/role")
    public ResponseEntity<?> updateRole(@RequestBody UpdateRoleRequest request, Authentication authentication) {
        if (request.username() == null || request.username().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "用户名不能为空"));
        }
        String role = request.role();
        if (role == null || !(User.ROLE_ADMIN.equals(role) || User.ROLE_USER.equals(role))) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "角色取值应为 admin / user"));
        }
        String caller = authentication != null ? authentication.getName() : null;
        if (caller != null && caller.equals(request.username()) && User.ROLE_USER.equals(role)) {
            return ResponseEntity.status(400).body(Map.of("code", 400, "message", "不能将自己降级为普通用户"));
        }
        User target = userService.getByUsername(request.username());
        if (target == null) {
            return ResponseEntity.status(404).body(Map.of("code", 404, "message", "用户不存在"));
        }
        if (role.equals(target.getRole())) {
            return ResponseEntity.ok(Map.of("code", 200, "message", "角色未变更"));
        }
        userService.updateRole(request.username(), role);
        // 切换角色后清空 session_token，强制对方重新登录以拿到新 role claim。
        userService.updateSessionToken(request.username(), null);
        log.info("admin updated role username={} -> {} by={}", request.username(), role, caller);
        return ResponseEntity.ok(Map.of("code", 200, "message", "角色已更新"));
    }

    /**
     * 启用或禁用账号。禁用会同时清空 session_token。
     * @param request 启停请求。
     * @param authentication 当前登录管理员。
     * @return 操作结果。
     */
    @PutMapping("/enabled")
    public ResponseEntity<?> updateEnabled(@RequestBody UpdateEnabledRequest request, Authentication authentication) {
        if (request.username() == null || request.username().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "用户名不能为空"));
        }
        if (request.enabled() == null) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "enabled 不能为空"));
        }
        String caller = authentication != null ? authentication.getName() : null;
        if (caller != null && caller.equals(request.username()) && Boolean.FALSE.equals(request.enabled())) {
            return ResponseEntity.status(400).body(Map.of("code", 400, "message", "不能禁用当前登录账号"));
        }
        User target = userService.getByUsername(request.username());
        if (target == null) {
            return ResponseEntity.status(404).body(Map.of("code", 404, "message", "用户不存在"));
        }
        boolean enabled = Boolean.TRUE.equals(request.enabled());
        userService.updateEnabled(request.username(), enabled);
        if (!enabled) {
            userService.updateSessionToken(request.username(), null);
        }
        log.info("admin updated enabled username={} -> {} by={}", request.username(), enabled, caller);
        return ResponseEntity.ok(Map.of("code", 200, "message", enabled ? "账号已启用" : "账号已禁用"));
    }

    private Map<String, Object> toListItem(User u) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", u.getId());
        m.put("username", u.getUsername());
        m.put("email", u.getEmail());
        m.put("role", u.getRole());
        m.put("avatar", u.getAvatar());
        m.put("gender", u.getGender());
        m.put("genderCustom", u.getGenderCustom());
        m.put("birthday", u.getBirthday() != null ? u.getBirthday().toString() : null);
        m.put("enabled", u.getEnabled() == null ? Boolean.TRUE : u.getEnabled());
        m.put("createdAt", u.getCreatedAt() != null ? u.getCreatedAt().toString() : "");
        return m;
    }

    /**
     * 变更角色请求体。
     * @param username 用户名。
     * @param role 新角色（admin / user）。
     */
    public record UpdateRoleRequest(String username, String role) {
    }

    /**
     * 启停请求体。
     * @param username 用户名。
     * @param enabled 是否启用。
     */
    public record UpdateEnabledRequest(String username, Boolean enabled) {
    }
}
