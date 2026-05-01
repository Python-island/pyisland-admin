package com.pyisland.server.user.controller;

import com.pyisland.server.user.entity.IdentityVerification;
import com.pyisland.server.user.service.IdentityVerificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
 * 管理面板：身份认证测试接口。
 * 仅限 ADMIN 角色调用，用于后台调试身份认证流程。
 */
@RestController
@RequestMapping("/v1/admin/identity")
@PreAuthorize("hasRole('ADMIN')")
public class IdentityAdminController {

    private static final Logger log = LoggerFactory.getLogger(IdentityAdminController.class);

    private final IdentityVerificationService verificationService;

    public IdentityAdminController(IdentityVerificationService verificationService) {
        this.verificationService = verificationService;
    }

    /**
     * 以指定用户身份发起身份认证测试。
     */
    @PostMapping("/test/start")
    public ResponseEntity<Map<String, Object>> testStart(@RequestBody TestStartRequest body) {
        try {
            if (body == null || body.username == null || body.username.isBlank()
                    || body.certName == null || body.certName.isBlank()
                    || body.certNo == null || body.certNo.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "用户名、姓名和身份证号不能为空"));
            }
            String username = body.username.trim();
            String certName = body.certName.trim();
            String certNo = body.certNo.trim();

            IdentityVerificationService.StartResult result =
                    verificationService.startVerification(username, certName, certNo);

            Map<String, Object> resp = new HashMap<>();
            resp.put("code", 200);
            resp.put("message", "认证发起成功");
            resp.put("data", Map.of(
                    "certifyId", result.certifyId(),
                    "certifyUrl", result.certifyUrl(),
                    "outerOrderNo", result.outerOrderNo()
            ));
            return ResponseEntity.ok(resp);
        } catch (IllegalStateException ex) {
            log.warn("admin identity test start failed err={}", ex.getMessage());
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", ex.getMessage()));
        } catch (Exception ex) {
            log.error("admin identity test start error", ex);
            return ResponseEntity.internalServerError().body(Map.of("code", 500, "message", "身份认证服务异常: " + ex.getMessage()));
        }
    }

    /**
     * 以指定用户身份查询认证结果。
     */
    @GetMapping("/test/query")
    public ResponseEntity<Map<String, Object>> testQuery(@RequestParam("username") String username,
                                                          @RequestParam("certifyId") String certifyId) {
        try {
            if (username == null || username.isBlank() || certifyId == null || certifyId.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "用户名和 certifyId 不能为空"));
            }
            IdentityVerificationService.VerifyResult result =
                    verificationService.queryAndUpdate(username.trim(), certifyId.trim());

            Map<String, Object> resp = new HashMap<>();
            resp.put("code", 200);
            resp.put("data", Map.of("passed", result.passed(), "message", result.message()));
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", ex.getMessage()));
        } catch (Exception ex) {
            log.error("admin identity test query error username={} certifyId={}", username, certifyId, ex);
            return ResponseEntity.internalServerError().body(Map.of("code", 500, "message", "查询异常: " + ex.getMessage()));
        }
    }

    /**
     * 查询指定用户的认证状态。
     */
    @GetMapping("/test/status")
    public ResponseEntity<Map<String, Object>> testStatus(@RequestParam("username") String username) {
        if (username == null || username.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "用户名不能为空"));
        }
        boolean verified = verificationService.isVerified(username.trim());
        return ResponseEntity.ok(Map.of("code", 200, "data", Map.of("verified", verified)));
    }

    /**
     * 查询指定用户的认证记录列表。
     */
    @GetMapping("/test/records")
    public ResponseEntity<Map<String, Object>> testRecords(@RequestParam("username") String username,
                                                            @RequestParam(value = "limit", defaultValue = "20") int limit) {
        if (username == null || username.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "用户名不能为空"));
        }
        List<IdentityVerification> list = verificationService.listRecords(username.trim(), limit);
        List<Map<String, Object>> records = list.stream().map(r -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", r.getId());
            m.put("username", r.getUsername());
            m.put("outerOrderNo", r.getOuterOrderNo());
            m.put("certifyId", r.getCertifyId());
            m.put("status", r.getStatus());
            m.put("materialInfoUrl", r.getMaterialInfoUrl());
            m.put("createdAt", r.getCreatedAt());
            m.put("updatedAt", r.getUpdatedAt());
            return m;
        }).toList();
        return ResponseEntity.ok(Map.of("code", 200, "data", records));
    }

    /**
     * 查询指定用户的实名信息（解密姓名 + 脱敏身份证号）。
     */
    @GetMapping("/user-info")
    public ResponseEntity<Map<String, Object>> userIdentityInfo(@RequestParam("username") String username) {
        if (username == null || username.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "用户名不能为空"));
        }
        IdentityVerificationService.IdentityInfo info = verificationService.getIdentityInfo(username.trim());
        if (info == null) {
            return ResponseEntity.ok(Map.of("code", 200, "data", Map.of("verified", false)));
        }
        Map<String, Object> data = new HashMap<>();
        data.put("verified", true);
        data.put("certName", info.certName());
        data.put("maskedCertNo", info.maskedCertNo());
        data.put("status", info.status());
        data.put("verifiedAt", info.verifiedAt());
        data.put("updatedAt", info.updatedAt());
        return ResponseEntity.ok(Map.of("code", 200, "data", data));
    }

    public static class TestStartRequest {
        public String username;
        public String certName;
        public String certNo;
    }
}
