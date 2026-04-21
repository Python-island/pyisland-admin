package com.pyisland.server.auth.controller;

import com.pyisland.server.auth.service.IssueFeedbackService;
import com.pyisland.server.auth.service.SliderCaptchaService;
import com.pyisland.server.common.util.ClientIpUtil;
import com.pyisland.server.user.entity.User;
import com.pyisland.server.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1")
public class IssueFeedbackController {

    private final IssueFeedbackService issueFeedbackService;
    private final SliderCaptchaService sliderCaptchaService;
    private final UserService userService;

    public IssueFeedbackController(IssueFeedbackService issueFeedbackService,
                                   SliderCaptchaService sliderCaptchaService,
                                   UserService userService) {
        this.issueFeedbackService = issueFeedbackService;
        this.sliderCaptchaService = sliderCaptchaService;
        this.userService = userService;
    }

    @PostMapping("/user/feedback/submit")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ResponseEntity<Map<String, Object>> submit(@RequestBody SubmitFeedbackRequest request,
                                                       Authentication authentication,
                                                       HttpServletRequest http) {
        String caller = callerName(authentication);
        if (caller == null) {
            return error(401, "未登录");
        }
        User user = userService.getByUsername(caller);
        if (user == null) {
            return error(404, "用户不存在");
        }

        String userIp = ClientIpUtil.resolve(http);
        SliderCaptchaService.VerifyResult captchaResult = sliderCaptchaService.verify(
                request.captchaTicket(),
                request.captchaRandstr(),
                userIp
        );
        if (!captchaResult.ok()) {
            return error(captchaResult.code(), captchaResult.message());
        }

        SliderCaptchaService.VerifyResult signResult = sliderCaptchaService.consumeSendSign(
                request.captchaSign(),
                user.getEmail(),
                userIp,
                request.captchaTicket()
        );
        if (!signResult.ok()) {
            return error(signResult.code(), signResult.message());
        }

        try {
            boolean ok = issueFeedbackService.submit(
                    caller,
                    userIp,
                    request.feedbackType(),
                    request.title(),
                    request.content(),
                    request.contact(),
                    request.clientVersion()
            );
            if (!ok) {
                return error(429, "提交过于频繁，请稍后再试");
            }
            return ok("反馈已提交");
        } catch (IllegalArgumentException ex) {
            return error(400, ex.getMessage());
        }
    }

    @GetMapping("/user/feedback/mine")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ResponseEntity<Map<String, Object>> listMine(@RequestParam(value = "status", required = false) String status,
                                                         @RequestParam(value = "page", defaultValue = "1") int page,
                                                         @RequestParam(value = "pageSize", defaultValue = "20") int pageSize,
                                                         Authentication authentication) {
        String caller = callerName(authentication);
        if (caller == null) {
            return error(401, "未登录");
        }
        List<Map<String, Object>> items = issueFeedbackService.listMine(caller, status, page, pageSize);
        long total = issueFeedbackService.countMine(caller, status);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", items);
        data.put("total", total);
        data.put("page", Math.max(1, page));
        data.put("pageSize", Math.min(100, Math.max(1, pageSize)));
        return okData("success", data);
    }

    @GetMapping("/admin/feedback")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> listAdmin(@RequestParam(value = "status", required = false) String status,
                                                          @RequestParam(value = "keyword", required = false) String keyword,
                                                          @RequestParam(value = "page", defaultValue = "1") int page,
                                                          @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        List<Map<String, Object>> items = issueFeedbackService.listAdmin(status, keyword, page, pageSize);
        long total = issueFeedbackService.countAdmin(status, keyword);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", items);
        data.put("total", total);
        data.put("page", Math.max(1, page));
        data.put("pageSize", Math.min(100, Math.max(1, pageSize)));
        return okData("success", data);
    }

    @PutMapping("/admin/feedback/resolve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> resolve(@RequestBody ResolveFeedbackRequest request) {
        boolean ok = issueFeedbackService.resolve(request.id(), request.status(), request.adminReply());
        if (!ok) {
            return error(400, "处理失败");
        }
        return ok("处理成功");
    }

    private String callerName(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return null;
        }
        return authentication.getName();
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
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("message", message);
        return ResponseEntity.status(code == 429 ? 429 : (code == 401 ? 401 : 400)).body(body);
    }

    public record SubmitFeedbackRequest(String feedbackType,
                                        String title,
                                        String content,
                                        String contact,
                                        String clientVersion,
                                        String captchaTicket,
                                        String captchaRandstr,
                                        String captchaSign) {
    }

    public record ResolveFeedbackRequest(Long id,
                                         String status,
                                         String adminReply) {
    }
}
