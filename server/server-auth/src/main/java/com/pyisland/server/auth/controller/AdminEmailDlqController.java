package com.pyisland.server.auth.controller;

import com.pyisland.server.auth.entity.EmailDispatchDlqLog;
import com.pyisland.server.auth.service.EmailVerificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 管理端邮件 DLQ 查询接口。
 */
@RestController
@RequestMapping("/v1/admin/email")
@PreAuthorize("hasRole('ADMIN')")
public class AdminEmailDlqController {

    private final EmailVerificationService emailVerificationService;

    public AdminEmailDlqController(EmailVerificationService emailVerificationService) {
        this.emailVerificationService = emailVerificationService;
    }

    @GetMapping("/notify-dlq")
    public ResponseEntity<?> listDispatchDlq(@RequestParam(required = false) String traceId,
                                             @RequestParam(required = false) String email,
                                             @RequestParam(defaultValue = "50") int limit) {
        List<EmailDispatchDlqLog> list = emailVerificationService.adminListDispatchDlq(traceId, email, limit);
        return ResponseEntity.ok(Map.of("code", 200, "message", "success", "data", list));
    }
}
