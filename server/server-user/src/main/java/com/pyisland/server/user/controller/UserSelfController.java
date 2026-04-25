package com.pyisland.server.user.controller;

import com.pyisland.server.common.util.ClientIpUtil;
import com.pyisland.server.user.entity.User;
import com.pyisland.server.user.policy.GenderPolicy;
import com.pyisland.server.user.policy.PasswordHashService;
import com.pyisland.server.user.policy.PasswordPolicy;
import com.pyisland.server.user.service.StaticAssetUrlService;
import com.pyisland.server.user.service.TotpSecurityService;
import com.pyisland.server.upload.service.R2StorageService;
import com.pyisland.server.user.service.UserService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.Duration;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.Map;

/**
 * 普通用户自助接口。面向已登录用户（role=user / pro / admin）开放，用户只能操作自身资源。
 * 所有路径以 /v1/user 开头，由 Spring Security 统一保护：hasAnyRole("USER","PRO","ADMIN")。
 */
@RestController
@RequestMapping("/v1/user")
@PreAuthorize("hasAnyRole('USER','PRO','ADMIN')")
public class UserSelfController {

    private static final Logger log = LoggerFactory.getLogger(UserSelfController.class);
    private static final String PASSWORD_EMAIL_CODE_SCENE = "RESET_PASSWORD";
    private static final String UNREGISTER_EMAIL_CODE_SCENE = "UNREGISTER";
    private static final int EMAIL_CODE_MAX_VERIFY_ATTEMPTS = 5;

    private final UserService userService;
    private final PasswordHashService passwordHashService;
    private final R2StorageService r2StorageService;
    private final StaticAssetUrlService staticAssetUrlService;
    private final StringRedisTemplate verificationRedisTemplate;
    private final String verifyCodePepper;
    private final TotpSecurityService totpSecurityService;

    @Value("${UPDATE_SOURCE_COS_URL:}")
    private String updateSourceCosUrl;
    @Value("${UPDATE_SOURCE_OSS_URL:}")
    private String updateSourceOssUrl;

    /**
     * 构造用户自助控制器。
     * @param userService 用户服务。
     * @param passwordHashService 密码哈希服务。
     * @param r2StorageService R2 存储服务，用于改写历史头像 URL。
     */
    public UserSelfController(UserService userService,
                              PasswordHashService passwordHashService,
                              R2StorageService r2StorageService,
                              StaticAssetUrlService staticAssetUrlService,
                              @Qualifier("verificationRedisTemplate") StringRedisTemplate verificationRedisTemplate,
                              @Value("${VERIFY_CODE_PEPPER:pyisland-verify-pepper}") String verifyCodePepper,
                              TotpSecurityService totpSecurityService) {
        this.userService = userService;
        this.passwordHashService = passwordHashService;
        this.r2StorageService = r2StorageService;
        this.staticAssetUrlService = staticAssetUrlService;
        this.verificationRedisTemplate = verificationRedisTemplate;
        this.verifyCodePepper = verifyCodePepper;
        this.totpSecurityService = totpSecurityService;
    }

    /**
     * 获取当前登录用户资料。
     * @param authentication 当前安全上下文。
     * @return 资料数据。
     */
    @GetMapping("/profile")
    public ResponseEntity<?> getProfile(Authentication authentication,
                                        @RequestHeader(value = StaticAssetUrlService.NODE_HEADER_NAME, required = false) String assetNode) {
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
        data.put("role", user.getRole());
        data.put("proExpireAt", user.getProExpireAt() != null ? user.getProExpireAt().toString() : null);
        String sourceAvatar = r2StorageService.rewriteLegacyUrl(userService.getAvatarByUsername(user.getUsername()));
        boolean proUser = User.ROLE_PRO.equalsIgnoreCase(user.getRole()) || User.ROLE_ADMIN.equalsIgnoreCase(user.getRole());
        data.put("avatar", staticAssetUrlService.rewriteUrl(sourceAvatar, assetNode, proUser));
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
                                            Authentication authentication,
                                            HttpServletRequest http) {
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
        if (request.emailCode() == null || request.emailCode().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "邮箱验证码不能为空"));
        }
        String emailCodeError = verifyEmailCodeOrMessage(PASSWORD_EMAIL_CODE_SCENE, user.getEmail(), request.emailCode());
        if (emailCodeError != null) {
            int statusCode = "验证码服务暂不可用".equals(emailCodeError) ? 503 : 401;
            return ResponseEntity.status(statusCode).body(Map.of("code", statusCode, "message", emailCodeError));
        }
        if (request.totpCode() == null || request.totpCode().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "TOTP 密令不能为空"));
        }
        String totpError = totpSecurityService.verifyTotpOrMessage(
                caller,
                ClientIpUtil.resolve(http),
                request.totpCode()
        );
        if (totpError != null) {
            int statusCode = "TOTP 种子无效".equals(totpError)
                    || "TOTP 密钥服务未配置".equals(totpError)
                    ? 503 : 401;
            return ResponseEntity.status(statusCode).body(Map.of("code", statusCode, "message", totpError));
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
     * 获取当前用户 TOTP Seed（Base32）。若不存在则自动初始化。
     * @param authentication 当前安全上下文。
     * @return Seed 信息。
     */
    @GetMapping("/profile/password/totp-seed")
    public ResponseEntity<?> getPasswordTotpSeed(Authentication authentication) {
        String caller = callerName(authentication);
        if (caller == null) {
            return ResponseEntity.status(401).body(Map.of("code", 401, "message", "未登录"));
        }
        String seed = totpSecurityService.getOrCreateTotpSeedForClient(caller);
        if (seed == null || seed.isBlank()) {
            return ResponseEntity.status(503).body(Map.of("code", 503, "message", "TOTP 种子不可用"));
        }
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "success",
                "data", Map.of(
                        "seed", seed,
                        "algorithm", "HMAC-SHA1",
                        "digits", TotpSecurityService.TOTP_DIGITS,
                        "periodSeconds", TotpSecurityService.TOTP_PERIOD_SECONDS
                )
        ));
    }

    /**
     * 轮换当前用户 TOTP Seed（Base32）。
     * @param authentication 当前安全上下文。
     * @return 新 Seed 信息。
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/profile/password/totp-seed/rotate")
    public ResponseEntity<?> rotatePasswordTotpSeed(Authentication authentication) {
        String caller = callerName(authentication);
        if (caller == null) {
            return ResponseEntity.status(401).body(Map.of("code", 401, "message", "未登录"));
        }
        String seed = totpSecurityService.rotateTotpSeedForClient(caller);
        if (seed == null || seed.isBlank()) {
            return ResponseEntity.status(503).body(Map.of("code", 503, "message", "TOTP 种子轮换失败"));
        }
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "success",
                "data", Map.of(
                        "seed", seed,
                        "algorithm", "HMAC-SHA1",
                        "digits", TotpSecurityService.TOTP_DIGITS,
                        "periodSeconds", TotpSecurityService.TOTP_PERIOD_SECONDS
                )
        ));
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
        if (request.emailCode() == null || request.emailCode().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "邮箱验证码不能为空"));
        }
        String emailCodeError = verifyEmailCodeOrMessage(UNREGISTER_EMAIL_CODE_SCENE, user.getEmail(), request.emailCode());
        if (emailCodeError != null) {
            int statusCode = "验证码服务暂不可用".equals(emailCodeError) ? 503 : 401;
            return ResponseEntity.status(statusCode).body(Map.of("code", statusCode, "message", emailCodeError));
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

    private String verifyEmailCodeOrMessage(String scene, String emailRaw, String codeRaw) {
        String email = emailRaw == null ? "" : emailRaw.trim().toLowerCase();
        String code = codeRaw == null ? "" : codeRaw.trim();
        if (email.isEmpty() || code.isEmpty()) {
            return "邮箱验证码不能为空";
        }
        try {
            String codeKey = keyEmailCode(scene, email);
            String attemptsKey = keyEmailCodeAttempts(scene, email);
            String storedHash = verificationRedisTemplate.opsForValue().get(codeKey);
            if (storedHash == null || storedHash.isBlank()) {
                return "验证码不存在或已过期";
            }
            String hashedInput = hashEmailCode(scene, email, code);
            if (!storedHash.equals(hashedInput)) {
                Long attempts = verificationRedisTemplate.opsForValue().increment(attemptsKey);
                Long ttl = verificationRedisTemplate.getExpire(codeKey);
                if (attempts != null && attempts == 1L && ttl != null && ttl > 0) {
                    verificationRedisTemplate.expire(attemptsKey, Duration.ofSeconds(ttl));
                }
                if (attempts != null && attempts >= EMAIL_CODE_MAX_VERIFY_ATTEMPTS) {
                    verificationRedisTemplate.delete(codeKey);
                    verificationRedisTemplate.delete(attemptsKey);
                    return "验证码错误次数过多，请重新获取";
                }
                return "验证码错误";
            }
            verificationRedisTemplate.delete(codeKey);
            verificationRedisTemplate.delete(attemptsKey);
            return null;
        } catch (Exception ex) {
            return "验证码服务暂不可用";
        }
    }

    private String keyEmailCode(String scene, String email) {
        return "verify:code:" + scene + ":" + email;
    }

    private String keyEmailCodeAttempts(String scene, String email) {
        return "verify:attempts:" + scene + ":" + email;
    }

    private String hashEmailCode(String scene, String email, String plainCode) {
        String raw = verifyCodePepper + "|" + scene + "|" + email + "|" + plainCode;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    /**
     * 获取 PRO 专属更新源下载地址。仅 PRO 角色可调用。
     * @param source 更新源标识（tencent-cos / aliyun-oss）。
     * @param authentication 当前安全上下文。
     * @return 更新源 URL。
     */
    @PreAuthorize("hasRole('PRO')")
    @GetMapping("/update-source")
    public ResponseEntity<?> getUpdateSourceUrl(@RequestParam String source,
                                                Authentication authentication) {
        String caller = callerName(authentication);
        if (caller == null) {
            return ResponseEntity.status(401).body(Map.of("code", 401, "message", "未登录"));
        }
        if (source == null || source.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "source 参数不能为空"));
        }
        String url;
        switch (source.trim().toLowerCase()) {
            case "tencent-cos" -> url = updateSourceCosUrl;
            case "aliyun-oss" -> url = updateSourceOssUrl;
            default -> {
                return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "不支持的更新源: " + source));
            }
        }
        if (url == null || url.isBlank()) {
            return ResponseEntity.status(503).body(Map.of("code", 503, "message", "该更新源暂未配置"));
        }
        log.info("update-source requested username={} source={}", caller, source);
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "success",
                "data", Map.of("url", url)
        ));
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
     * @param emailCode 邮箱验证码（RESET_PASSWORD 场景）。
     * @param totpCode TOTP 动态密令（6 位数字）。
     */
    public record UpdateSelfPasswordRequest(String password, String emailCode, String totpCode) {
    }

    /**
     * 注销账号请求体。
     * @param password 当前密码用于二次确认。
     * @param emailCode 邮箱验证码（UNREGISTER 场景）。
     */
    public record UnregisterRequest(String password, String emailCode) {
    }
}
