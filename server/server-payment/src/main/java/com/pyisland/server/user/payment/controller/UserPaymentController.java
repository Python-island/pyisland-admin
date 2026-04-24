package com.pyisland.server.user.payment.controller;

import com.pyisland.server.user.entity.User;
import com.pyisland.server.user.payment.entity.PaymentOrder;
import com.pyisland.server.user.payment.service.PaymentService;
import com.pyisland.server.user.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    public ResponseEntity<?> createProMonthOrder(Authentication authentication) {
        String caller = caller(authentication);
        if (caller == null) {
            return ResponseEntity.status(401).body(Map.of("code", 401, "message", "未登录"));
        }
        try {
            PaymentOrder order = paymentService.createProMonthOrder(caller);
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

    private String caller(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return null;
        }
        return authentication.getName();
    }
}
