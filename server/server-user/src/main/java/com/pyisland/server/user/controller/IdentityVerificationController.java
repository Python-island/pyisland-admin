package com.pyisland.server.user.controller;

import com.pyisland.server.user.entity.IdentityVerification;
import com.pyisland.server.user.service.IdentityVerificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 身份认证接口。面向已登录用户开放。
 */
@RestController
@RequestMapping("/v1/identity")
@PreAuthorize("hasAnyRole('USER','PRO','ADMIN')")
public class IdentityVerificationController {

    private static final Logger log = LoggerFactory.getLogger(IdentityVerificationController.class);

    private final IdentityVerificationService verificationService;

    public IdentityVerificationController(IdentityVerificationService verificationService) {
        this.verificationService = verificationService;
    }

    /**
     * 发起身份认证。
     * 前端传入姓名和身份证号，服务端调用支付宝初始化+认证接口，返回认证页面 URL。
     */
    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> start(@RequestBody StartRequest body,
                                                     Authentication authentication) {
        String username = authentication.getName();
        try {
            if (body == null || body.certName == null || body.certName.isBlank()
                    || body.certNo == null || body.certNo.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "姓名和身份证号不能为空"));
            }
            String certName = body.certName.trim();
            String certNo = body.certNo.trim();
            if (!isValidCertNo(certNo)) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "身份证号格式不正确"));
            }

            IdentityVerificationService.StartResult result = verificationService.startVerification(username, certName, certNo);

            Map<String, Object> resp = new HashMap<>();
            resp.put("success", true);
            resp.put("certifyId", result.certifyId());
            resp.put("certifyUrl", result.certifyUrl());
            resp.put("outerOrderNo", result.outerOrderNo());
            return ResponseEntity.ok(resp);
        } catch (IllegalStateException ex) {
            log.warn("identity start failed username={} err={}", username, ex.getMessage());
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", ex.getMessage()));
        } catch (Exception ex) {
            log.error("identity start error username={}", username, ex);
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message", "身份认证服务异常，请稍后重试"));
        }
    }

    /**
     * 查询认证结果。
     * 用户完成支付宝侧人脸认证后，前端调用此接口查询结果。
     */
    @GetMapping("/query")
    public ResponseEntity<Map<String, Object>> query(@RequestParam("certifyId") String certifyId,
                                                     Authentication authentication) {
        String username = authentication.getName();
        try {
            if (certifyId == null || certifyId.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "certifyId 不能为空"));
            }

            IdentityVerificationService.VerifyResult result = verificationService.queryAndUpdate(username, certifyId.trim());

            Map<String, Object> resp = new HashMap<>();
            resp.put("success", true);
            resp.put("passed", result.passed());
            resp.put("message", result.message());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", ex.getMessage()));
        } catch (Exception ex) {
            log.error("identity query error username={} certifyId={}", username, certifyId, ex);
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message", "查询认证结果异常，请稍后重试"));
        }
    }

    /**
     * 查询当前用户是否已通过实名认证。
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status(Authentication authentication) {
        String username = authentication.getName();
        boolean verified = verificationService.isVerified(username);
        return ResponseEntity.ok(Map.of("success", true, "verified", verified));
    }

    /**
     * 查询当前用户认证记录列表。
     */
    @GetMapping("/records")
    public ResponseEntity<Map<String, Object>> records(@RequestParam(value = "limit", defaultValue = "10") int limit,
                                                       Authentication authentication) {
        String username = authentication.getName();
        List<IdentityVerification> list = verificationService.listRecords(username, limit);
        // 脱敏：不返回密文字段
        List<Map<String, Object>> sanitized = list.stream().map(r -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", r.getId());
            m.put("outerOrderNo", r.getOuterOrderNo());
            m.put("certifyId", r.getCertifyId());
            m.put("status", r.getStatus());
            m.put("createdAt", r.getCreatedAt());
            m.put("updatedAt", r.getUpdatedAt());
            return m;
        }).toList();
        return ResponseEntity.ok(Map.of("success", true, "records", sanitized));
    }

    private boolean isValidCertNo(String certNo) {
        return certNo != null && certNo.matches("^\\d{17}[\\dXx]$");
    }

    public static class StartRequest {
        public String certName;
        public String certNo;
    }
}
