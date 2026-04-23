package com.pyisland.server.user.payment.controller;

import com.pyisland.server.user.payment.config.WechatPayProperties;
import com.pyisland.server.user.payment.entity.PaymentOrder;
import com.pyisland.server.user.payment.service.PaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 管理端支付查询接口。
 */
@RestController
@RequestMapping("/v1/admin/payment")
@PreAuthorize("hasRole('ADMIN')")
public class AdminPaymentController {

    private final PaymentService paymentService;
    private final WechatPayProperties wechatPayProperties;

    public AdminPaymentController(PaymentService paymentService,
                                  WechatPayProperties wechatPayProperties) {
        this.paymentService = paymentService;
        this.wechatPayProperties = wechatPayProperties;
    }

    @GetMapping("/orders")
    public ResponseEntity<?> listOrders(@RequestParam(required = false) String username,
                                        @RequestParam(required = false) String status,
                                        @RequestParam(defaultValue = "100") int limit) {
        List<PaymentOrder> list = paymentService.adminList(username, status, limit);
        return ResponseEntity.ok(Map.of("code", 200, "message", "success", "data", list));
    }

    @GetMapping("/config")
    public ResponseEntity<?> getConfig() {
        Map<String, Object> data = Map.ofEntries(
                Map.entry("enabled", wechatPayProperties.isEnabled()),
                Map.entry("mchId", blankToEmpty(wechatPayProperties.getMchId())),
                Map.entry("appId", blankToEmpty(wechatPayProperties.getAppId())),
                Map.entry("serialNo", blankToEmpty(wechatPayProperties.getSerialNo())),
                Map.entry("notifyUrl", blankToEmpty(wechatPayProperties.getNotifyUrl())),
                Map.entry("publicKeyId", blankToEmpty(wechatPayProperties.getPublicKeyId())),
                Map.entry("publicKeyPath", blankToEmpty(wechatPayProperties.getPublicKeyPath())),
                Map.entry("platformCertPath", blankToEmpty(wechatPayProperties.getPlatformCertPath())),
                Map.entry("privateKeyPath", blankToEmpty(wechatPayProperties.getPrivateKeyPath())),
                Map.entry("apiV3KeyMasked", maskSecret(wechatPayProperties.getApiV3Key())),
                Map.entry("orderExpireMinutes", wechatPayProperties.getOrderExpireMinutes()),
                Map.entry("queryPendingBatchSize", wechatPayProperties.getQueryPendingBatchSize())
        );
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "success",
                "data", data
        ));
    }

    @PutMapping("/config")
    public ResponseEntity<?> updateConfig(@RequestBody ConfigUpdateRequest request) {
        if (request == null) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "请求体不能为空"));
        }
        if (request.orderExpireMinutes() != null && request.orderExpireMinutes() < 5) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "orderExpireMinutes 不能小于 5"));
        }
        if (request.queryPendingBatchSize() != null && request.queryPendingBatchSize() < 1) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "queryPendingBatchSize 不能小于 1"));
        }

        if (request.enabled() != null) {
            wechatPayProperties.setEnabled(request.enabled());
        }
        if (request.mchId() != null) {
            wechatPayProperties.setMchId(request.mchId().trim());
        }
        if (request.appId() != null) {
            wechatPayProperties.setAppId(request.appId().trim());
        }
        if (request.serialNo() != null) {
            wechatPayProperties.setSerialNo(request.serialNo().trim());
        }
        if (request.notifyUrl() != null) {
            wechatPayProperties.setNotifyUrl(request.notifyUrl().trim());
        }
        if (request.publicKeyId() != null) {
            wechatPayProperties.setPublicKeyId(request.publicKeyId().trim());
        }
        if (request.publicKeyPath() != null) {
            wechatPayProperties.setPublicKeyPath(request.publicKeyPath().trim());
        }
        if (request.platformCertPath() != null) {
            wechatPayProperties.setPlatformCertPath(request.platformCertPath().trim());
        }
        if (request.privateKeyPath() != null) {
            wechatPayProperties.setPrivateKeyPath(request.privateKeyPath().trim());
        }
        if (request.apiV3Key() != null) {
            wechatPayProperties.setApiV3Key(request.apiV3Key().trim());
        }
        if (request.orderExpireMinutes() != null) {
            wechatPayProperties.setOrderExpireMinutes(request.orderExpireMinutes());
        }
        if (request.queryPendingBatchSize() != null) {
            wechatPayProperties.setQueryPendingBatchSize(request.queryPendingBatchSize());
        }

        return ResponseEntity.ok(Map.of("code", 200, "message", "支付配置已更新（仅当前运行实例生效）"));
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String maskSecret(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        int visible = Math.min(4, value.length());
        return "*".repeat(Math.max(0, value.length() - visible)) + value.substring(value.length() - visible);
    }

    public record ConfigUpdateRequest(Boolean enabled,
                                      String mchId,
                                      String appId,
                                      String apiV3Key,
                                      String privateKeyPath,
                                      String serialNo,
                                      String notifyUrl,
                                      String publicKeyId,
                                      String publicKeyPath,
                                      String platformCertPath,
                                      Integer orderExpireMinutes,
                                      Integer queryPendingBatchSize) {
    }
}
