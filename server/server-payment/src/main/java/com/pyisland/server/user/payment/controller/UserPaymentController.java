package com.pyisland.server.user.payment.controller;

import com.pyisland.server.user.entity.User;
import com.pyisland.server.user.payment.entity.PaymentOrder;
import com.pyisland.server.user.payment.service.PaymentChannel;
import com.pyisland.server.user.payment.service.PaymentService;
import com.pyisland.server.user.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 用户支付接口。
 */
@RestController
@RequestMapping("/v1/user/payment")
@PreAuthorize("hasAnyRole('USER','PRO','ADMIN')")
public class UserPaymentController {

    private final PaymentService paymentService;
    private final UserService userService;

    public UserPaymentController(PaymentService paymentService,
                                 UserService userService) {
        this.paymentService = paymentService;
        this.userService = userService;
    }

    @PostMapping("/orders/pro-month")
    public ResponseEntity<?> createProMonthOrder(Authentication authentication,
                                                 @RequestParam(value = "channel", required = false) String channel,
                                                 @RequestParam("email") String email) {
        String caller = caller(authentication);
        if (caller == null) {
            return ResponseEntity.status(401).body(Map.of("code", 401, "message", "未登录"));
        }
        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "email 不能为空"));
        }
        PaymentChannel paymentChannel;
        if (channel == null || channel.isBlank()) {
            paymentChannel = PaymentChannel.WECHAT;
        } else {
            try {
                paymentChannel = PaymentChannel.valueOf(channel.trim().toUpperCase());
            } catch (IllegalArgumentException ex) {
                return ResponseEntity.badRequest().body(Map.of(
                        "code", 400,
                        "message", "不支持的支付通道，仅支持 WECHAT 或 ALIPAY"
                ));
            }
        }
        try {
            PaymentOrder order = paymentService.createProMonthOrder(caller, paymentChannel, email);
            User user = userService.getByUsername(caller);
            return ResponseEntity.ok(Map.of(
                    "code", 200,
                    "message", "success",
                    "data", paymentService.toOrderPayload(order, user)
            ));
        } catch (Exception ex) {
            return ResponseEntity.status(500).body(Map.of(
                    "code", 500,
                    "message", "创建支付订单失败: " + ex.getMessage()
            ));
        }
    }

    @GetMapping("/pricing/pro-month")
    public ResponseEntity<?> getProMonthPricing(Authentication authentication) {
        String caller = caller(authentication);
        if (caller == null) {
            return ResponseEntity.status(401).body(Map.of("code", 401, "message", "未登录"));
        }
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "success",
                "data", paymentService.getProMonthPricingPayload()
        ));
    }

    @GetMapping("/channels")
    public ResponseEntity<?> getPaymentChannels(Authentication authentication) {
        String caller = caller(authentication);
        if (caller == null) {
            return ResponseEntity.status(401).body(Map.of("code", 401, "message", "未登录"));
        }
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "success",
                "data", paymentService.getPaymentChannelsPayload()
        ));
    }

    @GetMapping("/orders/{outTradeNo}")
    public ResponseEntity<?> getOrder(@PathVariable String outTradeNo,
                                      Authentication authentication) {
        String caller = caller(authentication);
        if (caller == null) {
            return ResponseEntity.status(401).body(Map.of("code", 401, "message", "未登录"));
        }
        PaymentOrder order = paymentService.findOrder(outTradeNo);
        if (order == null || !caller.equals(order.getUsername())) {
            return ResponseEntity.status(404).body(Map.of("code", 404, "message", "订单不存在"));
        }
        order = paymentService.refreshOrderIfNeeded(order);
        User user = userService.getByUsername(caller);
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "success",
                "data", paymentService.toOrderPayload(order, user)
        ));
    }

    @GetMapping("/orders")
    public ResponseEntity<?> listOrders(@RequestParam(value = "limit", required = false, defaultValue = "20") Integer limit,
                                        Authentication authentication) {
        String caller = caller(authentication);
        if (caller == null) {
            return ResponseEntity.status(401).body(Map.of("code", 401, "message", "未登录"));
        }
        int normalizedLimit = Math.max(1, Math.min(limit == null ? 20 : limit, 50));
        User user = userService.getByUsername(caller);
        List<Map<String, Object>> data = paymentService.listUserOrders(caller, normalizedLimit).stream()
                .map((order) -> paymentService.toOrderPayload(order, user))
                .toList();
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "success",
                "data", data
        ));
    }

    @PostMapping("/orders/{outTradeNo}/close")
    public ResponseEntity<?> closeOrder(@PathVariable String outTradeNo,
                                        Authentication authentication) {
        String caller = caller(authentication);
        if (caller == null) {
            return ResponseEntity.status(401).body(Map.of("code", 401, "message", "未登录"));
        }
        boolean closed = paymentService.closeOrderForUser(caller, outTradeNo);
        if (!closed) {
            return ResponseEntity.status(400).body(Map.of("code", 400, "message", "订单关闭失败或不可关闭"));
        }
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "success"
        ));
    }

    private String caller(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return null;
        }
        return authentication.getName();
    }
}
