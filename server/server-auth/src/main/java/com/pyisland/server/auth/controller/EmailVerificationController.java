package com.pyisland.server.auth.controller;

import com.pyisland.server.auth.service.EmailVerificationService;
import com.pyisland.server.auth.service.SliderCaptchaService;
import com.pyisland.server.common.util.ClientIpUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 邮箱验证码接口。
 */
@RestController
@RequestMapping("/auth/user/email-code")
public class EmailVerificationController {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final EmailVerificationService emailVerificationService;
    private final SliderCaptchaService sliderCaptchaService;

    public EmailVerificationController(EmailVerificationService emailVerificationService,
                                       SliderCaptchaService sliderCaptchaService) {
        this.emailVerificationService = emailVerificationService;
        this.sliderCaptchaService = sliderCaptchaService;
    }

    @GetMapping("/captcha-config")
    public ResponseEntity<Map<String, Object>> captchaConfig() {
        SliderCaptchaService.CaptchaConfig config = sliderCaptchaService.currentConfig();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("enabled", config.enabled());
        data.put("provider", config.provider());
        data.put("minValue", config.minValue());
        data.put("maxValue", config.maxValue());
        data.put("tolerance", config.tolerance());
        data.put("challengeTtlSeconds", config.challengeTtlSeconds());
        return okData("success", data);
    }

    @PostMapping("/captcha-challenge")
    public ResponseEntity<Map<String, Object>> captchaChallenge() {
        try {
            SliderCaptchaService.CaptchaChallenge challenge = sliderCaptchaService.createChallenge();
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("challengeId", challenge.challengeId());
            data.put("minValue", challenge.minValue());
            data.put("maxValue", challenge.maxValue());
            data.put("targetValue", challenge.targetValue());
            data.put("tolerance", challenge.tolerance());
            return okData("success", data);
        } catch (Exception ex) {
            return error(503, "滑块验证码服务暂不可用");
        }
    }

    /**
     * 发送邮箱验证码。
     */
    @PostMapping("/send")
    public ResponseEntity<Map<String, Object>> sendCode(@RequestBody SendCodeRequest request, HttpServletRequest http) {
        if (request == null || request.email() == null || request.email().isBlank()) {
            return error(400, "邮箱不能为空");
        }
        if (request.scene() == null || request.scene().isBlank()) {
            return error(400, "场景不能为空");
        }

        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (!EMAIL_PATTERN.matcher(email).matches() || email.length() > 150) {
            return error(400, "邮箱格式不正确");
        }

        EmailVerificationService.Scene scene = parseScene(request.scene());
        if (scene == null) {
            return error(400, "不支持的验证码场景");
        }

        SliderCaptchaService.VerifyResult captchaResult = sliderCaptchaService.verify(
                request.captchaTicket(),
                request.captchaRandstr(),
                ClientIpUtil.resolve(http)
        );
        if (!captchaResult.ok()) {
            return error(captchaResult.code(), captchaResult.message());
        }

        EmailVerificationService.SendCodeResult result = emailVerificationService.sendCode(
                new EmailVerificationService.SendCodeCommand(email, scene, ClientIpUtil.resolve(http))
        );

        if (!result.ok()) {
            Map<String, Object> data = new LinkedHashMap<>();
            if (result.retryAfterSeconds() > 0) {
                data.put("retryAfterSeconds", result.retryAfterSeconds());
            }
            return errorWithData(result.code(), result.message(), data);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        if (result.retryAfterSeconds() > 0) {
            data.put("retryAfterSeconds", result.retryAfterSeconds());
        }
        return okData(result.message(), data);
    }

    /**
     * 校验邮箱验证码。
     */
    @PostMapping("/verify")
    public ResponseEntity<Map<String, Object>> verifyCode(@RequestBody VerifyCodeRequest request) {
        if (request == null || request.email() == null || request.email().isBlank()) {
            return error(400, "邮箱不能为空");
        }
        if (request.scene() == null || request.scene().isBlank()) {
            return error(400, "场景不能为空");
        }
        if (request.code() == null || request.code().isBlank()) {
            return error(400, "验证码不能为空");
        }

        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (!EMAIL_PATTERN.matcher(email).matches() || email.length() > 150) {
            return error(400, "邮箱格式不正确");
        }

        EmailVerificationService.Scene scene = parseScene(request.scene());
        if (scene == null) {
            return error(400, "不支持的验证码场景");
        }

        EmailVerificationService.VerifyCodeResult result = emailVerificationService.verifyCode(
                new EmailVerificationService.VerifyCodeCommand(
                        email,
                        scene,
                        request.code().trim(),
                        request.consume() == null || request.consume()
                )
        );

        if (!result.ok()) {
            return error(result.code(), result.message());
        }
        return ok(result.message());
    }

    private EmailVerificationService.Scene parseScene(String raw) {
        try {
            return EmailVerificationService.Scene.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ex) {
            return null;
        }
    }

    private ResponseEntity<Map<String, Object>> ok(String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", 200);
        body.put("message", message);
        return ResponseEntity.ok(body);
    }

    private ResponseEntity<Map<String, Object>> okData(String message, Object data) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", 200);
        body.put("message", message);
        body.put("data", data);
        return ResponseEntity.ok(body);
    }

    private ResponseEntity<Map<String, Object>> error(int code, String message) {
        return errorWithData(code, message, null);
    }

    private ResponseEntity<Map<String, Object>> errorWithData(int code, String message, Object data) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("message", message);
        if (data != null) {
            body.put("data", data);
        }
        return ResponseEntity.status(code == 429 ? 429 : (code == 401 ? 401 : 400)).body(body);
    }

    /**
     * 发码请求。
     * @param email 目标邮箱。
     * @param scene 场景，支持 REGISTER/LOGIN/RESET_PASSWORD/CHANGE_EMAIL。
     * @param captchaTicket 滑块票据。
     * @param captchaRandstr 滑块随机串。
     */
    public record SendCodeRequest(String email, String scene, String captchaTicket, String captchaRandstr) {
    }

    /**
     * 验证请求。
     * @param email 目标邮箱。
     * @param scene 场景，支持 REGISTER/LOGIN/RESET_PASSWORD/CHANGE_EMAIL。
     * @param code 验证码。
     * @param consume 是否消费验证码；默认 true。
     */
    public record VerifyCodeRequest(String email, String scene, String code, Boolean consume) {
    }
}
