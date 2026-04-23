package com.pyisland.server.user.payment.controller;

import com.pyisland.server.user.payment.entity.PaymentOrder;
import com.pyisland.server.user.payment.service.PaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
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

    public AdminPaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping("/orders")
    public ResponseEntity<?> listOrders(@RequestParam(required = false) String username,
                                        @RequestParam(required = false) String status,
                                        @RequestParam(defaultValue = "100") int limit) {
        List<PaymentOrder> list = paymentService.adminList(username, status, limit);
        return ResponseEntity.ok(Map.of("code", 200, "message", "success", "data", list));
    }
}
