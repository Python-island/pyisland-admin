package com.pyisland.server.user.payment.controller;

import com.pyisland.server.user.payment.config.AlipayProperties;
import com.pyisland.server.user.payment.config.WechatPayProperties;
import com.pyisland.server.user.payment.entity.PaymentDlqLog;
import com.pyisland.server.user.payment.entity.PaymentOrder;
import com.pyisland.server.user.payment.service.PaymentChannel;
import com.pyisland.server.user.payment.service.PaymentService;
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

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 管理端支付查询接口。
 */
@RestController
@RequestMapping("/v1/admin/payment")
@PreAuthorize("hasRole('ADMIN')")
public class AdminPaymentController {

    private final PaymentService paymentService;
    private final WechatPayProperties wechatPayProperties;
    private final AlipayProperties alipayProperties;

    public AdminPaymentController(PaymentService paymentService,
                                  WechatPayProperties wechatPayProperties,
                                  AlipayProperties alipayProperties) {
        this.paymentService = paymentService;
        this.wechatPayProperties = wechatPayProperties;
        this.alipayProperties = alipayProperties;
    }

    @GetMapping("/orders")
    public ResponseEntity<?> listOrders(@RequestParam(required = false) String username,
                                        @RequestParam(required = false) String status,
                                        @RequestParam(required = false) String channel,
                                        @RequestParam(defaultValue = "100") int limit) {
        List<PaymentOrder> list = paymentService.adminList(username, status, limit);
        String normalizedChannel = channel == null ? "" : channel.trim().toUpperCase();
        List<Map<String, Object>> payload = list.stream()
                .map(paymentService::toAdminOrderPayload)
                .filter(item -> normalizedChannel.isBlank()
                        || normalizedChannel.equals(String.valueOf(item.get("channel"))))
                .collect(Collectors.toList());
        return ResponseEntity.ok(Map.of("code", 200, "message", "success", "data", payload));
    }

    @GetMapping("/notify-dlq")
    public ResponseEntity<?> listNotifyDlq(@RequestParam(required = false) String notifyId,
                                           @RequestParam(required = false) String outTradeNo,
                                           @RequestParam(defaultValue = "50") int limit) {
        List<PaymentDlqLog> list = paymentService.adminListDlq(notifyId, outTradeNo, limit);
        return ResponseEntity.ok(Map.of("code", 200, "message", "success", "data", list));
    }

    @GetMapping("/receipt-dlq")
    public ResponseEntity<?> listReceiptDlq(@RequestParam(required = false) String traceId,
                                            @RequestParam(required = false) String outTradeNo,
                                            @RequestParam(defaultValue = "50") int limit) {
        List<PaymentDlqLog> list = paymentService.adminListReceiptDlq(traceId, outTradeNo, limit);
        return ResponseEntity.ok(Map.of("code", 200, "message", "success", "data", list));
    }

    @PutMapping("/orders/refresh")
    public ResponseEntity<?> refreshOrder(@RequestBody OrderActionRequest request) {
        if (request == null || request.outTradeNo() == null || request.outTradeNo().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "outTradeNo 不能为空"));
        }
        PaymentOrder refreshed = paymentService.adminRefreshOrder(request.outTradeNo());
        if (refreshed == null) {
            return ResponseEntity.status(404).body(Map.of("code", 404, "message", "订单不存在"));
        }
        return ResponseEntity.ok(Map.of("code", 200, "message", "success", "data", refreshed));
    }

    @PutMapping("/orders/close")
    public ResponseEntity<?> closeOrder(@RequestBody OrderActionRequest request) {
        if (request == null || request.outTradeNo() == null || request.outTradeNo().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "outTradeNo 不能为空"));
        }
        boolean closed = paymentService.adminCloseOrder(request.outTradeNo());
        if (!closed) {
            return ResponseEntity.status(400).body(Map.of("code", 400, "message", "仅 PAYING 状态订单可关闭"));
        }
        return ResponseEntity.ok(Map.of("code", 200, "message", "订单已关闭"));
    }

    @PostMapping("/orders/test")
    public ResponseEntity<?> createTestOrder(@RequestBody CreateTestOrderRequest request,
                                             Authentication authentication) {
        if (request == null || request.amountFen() == null) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "amountFen 不能为空"));
        }
        if (request.amountFen() < 1 || request.amountFen() > 1_000_000) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "amountFen 需在 1~1000000 分之间"));
        }
        String caller = authentication == null ? null : authentication.getName();
        if (caller == null || caller.isBlank()) {
            return ResponseEntity.status(401).body(Map.of("code", 401, "message", "未登录"));
        }

        PaymentChannel channel;
        if (request.channel() == null || request.channel().isBlank()) {
            channel = PaymentChannel.WECHAT;
        } else {
            try {
                channel = PaymentChannel.valueOf(request.channel().trim().toUpperCase());
            } catch (IllegalArgumentException ex) {
                return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "仅支持 WECHAT 或 ALIPAY"));
            }
        }

        try {
            PaymentOrder order = paymentService.createAdminTestOrder(caller, channel, request.amountFen(), request.subject());
            return ResponseEntity.ok(Map.of(
                    "code", 200,
                    "message", "success",
                    "data", paymentService.toOrderPayload(order, null)
            ));
        } catch (Exception ex) {
            return ResponseEntity.status(500).body(Map.of(
                    "code", 500,
                    "message", "创建测试单失败: " + ex.getMessage()
            ));
        }
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
                Map.entry("alipayEnabled", alipayProperties.isEnabled()),
                Map.entry("alipayGatewayUrl", blankToEmpty(alipayProperties.getGatewayUrl())),
                Map.entry("alipayAppId", blankToEmpty(alipayProperties.getAppId())),
                Map.entry("alipayNotifyUrl", blankToEmpty(alipayProperties.getNotifyUrl())),
                Map.entry("alipayPrivateKeyPath", blankToEmpty(alipayProperties.getPrivateKeyPath())),
                Map.entry("alipayPublicKeyPath", blankToEmpty(alipayProperties.getPublicKeyPath())),
                Map.entry("alipaySignType", blankToEmpty(alipayProperties.getSignType())),
                Map.entry("alipayCharset", blankToEmpty(alipayProperties.getCharset())),
                Map.entry("alipayQueryPendingBatchSize", alipayProperties.getQueryPendingBatchSize()),
                Map.entry("proMonthAmountFen", paymentService.getProMonthAmountFen()),
                Map.entry("freeDesc", paymentService.getFreePlanDesc()),
                Map.entry("freeFeatures", paymentService.getFreePlanFeatures()),
                Map.entry("proDesc", paymentService.getProPlanDesc()),
                Map.entry("proFeatures", paymentService.getProPlanFeatures()),
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
        if (request.alipayQueryPendingBatchSize() != null && request.alipayQueryPendingBatchSize() < 1) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "alipayQueryPendingBatchSize 不能小于 1"));
        }
        if (request.proMonthAmountFen() != null && request.proMonthAmountFen() < 1) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "proMonthAmountFen 不能小于 1"));
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
        if (request.alipayEnabled() != null) {
            alipayProperties.setEnabled(request.alipayEnabled());
        }
        if (request.alipayGatewayUrl() != null) {
            alipayProperties.setGatewayUrl(request.alipayGatewayUrl().trim());
        }
        if (request.alipayAppId() != null) {
            alipayProperties.setAppId(request.alipayAppId().trim());
        }
        if (request.alipayNotifyUrl() != null) {
            alipayProperties.setNotifyUrl(request.alipayNotifyUrl().trim());
        }
        if (request.alipayPrivateKeyPath() != null) {
            alipayProperties.setPrivateKeyPath(request.alipayPrivateKeyPath().trim());
        }
        if (request.alipayPublicKeyPath() != null) {
            alipayProperties.setPublicKeyPath(request.alipayPublicKeyPath().trim());
        }
        if (request.alipaySignType() != null) {
            alipayProperties.setSignType(request.alipaySignType().trim());
        }
        if (request.alipayCharset() != null) {
            alipayProperties.setCharset(request.alipayCharset().trim());
        }
        if (request.alipayQueryPendingBatchSize() != null) {
            alipayProperties.setQueryPendingBatchSize(request.alipayQueryPendingBatchSize());
        }
        if (request.proMonthAmountFen() != null) {
            paymentService.setProMonthAmountFen(request.proMonthAmountFen());
        }
        if (request.freeDesc() != null) {
            paymentService.setFreePlanDesc(request.freeDesc());
        }
        if (request.proDesc() != null) {
            paymentService.setProPlanDesc(request.proDesc());
        }
        if (request.freeFeatures() != null) {
            paymentService.setFreePlanFeatures(request.freeFeatures());
        }
        if (request.proFeatures() != null) {
            paymentService.setProPlanFeatures(request.proFeatures());
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
                                      Boolean alipayEnabled,
                                      String alipayGatewayUrl,
                                      String alipayAppId,
                                      String alipayNotifyUrl,
                                      String alipayPrivateKeyPath,
                                      String alipayPublicKeyPath,
                                      String alipaySignType,
                                      String alipayCharset,
                                      Integer alipayQueryPendingBatchSize,
                                      Integer proMonthAmountFen,
                                      String freeDesc,
                                      List<String> freeFeatures,
                                      String proDesc,
                                      List<String> proFeatures,
                                      Integer orderExpireMinutes,
                                      Integer queryPendingBatchSize) {
    }

    public record OrderActionRequest(String outTradeNo) {
    }

    public record CreateTestOrderRequest(String channel,
                                         Integer amountFen,
                                         String subject) {
    }
}
