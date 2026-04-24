package com.pyisland.server.user.payment.controller;

import com.pyisland.server.user.payment.config.PaymentMqConfig;
import com.pyisland.server.user.payment.mq.PaymentNotifyMessage;
import com.pyisland.server.user.payment.service.AlipayNotifyService;
import com.pyisland.server.user.payment.service.PaymentChannel;
import com.pyisland.server.user.payment.service.PaymentService;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 支付宝支付回调。
 */
@RestController
@RequestMapping("/v1/payment/alipay")
public class AlipayNotifyController {

    private final AlipayNotifyService notifyService;
    private final PaymentService paymentService;
    private final RabbitTemplate rabbitTemplate;

    public AlipayNotifyController(AlipayNotifyService notifyService,
                                  PaymentService paymentService,
                                  RabbitTemplate rabbitTemplate) {
        this.notifyService = notifyService;
        this.paymentService = paymentService;
        this.rabbitTemplate = rabbitTemplate;
    }

    @PostMapping("/notify")
    public ResponseEntity<String> notify(@RequestParam Map<String, String> params) {
        try {
            AlipayNotifyService.NotifyData notifyData = notifyService.parse(params);
            boolean verifyOk = notifyData.verifyOk()
                    && paymentService.validateAlipayNotifyBusiness(notifyData.outTradeNo(), notifyData.totalAmountFen());
            paymentService.logNotify(
                    notifyData.notifyId(),
                    notifyData.outTradeNo(),
                    notifyData.eventType(),
                    verifyOk,
                    "QUEUED",
                    notifyData.rawBody()
            );

            rabbitTemplate.convertAndSend(
                    PaymentMqConfig.PAYMENT_NOTIFY_EXCHANGE,
                    PaymentMqConfig.PAYMENT_NOTIFY_ROUTING_KEY,
                    new PaymentNotifyMessage(
                            notifyData.notifyId(),
                            PaymentChannel.ALIPAY.name(),
                            notifyData.outTradeNo(),
                            notifyData.transactionId(),
                            notifyData.tradeState(),
                            notifyData.successTime(),
                            verifyOk,
                            notifyData.rawBody()
                    )
            );
            return ResponseEntity.ok("success");
        } catch (Exception ex) {
            paymentService.logNotify(null, null, "UNKNOWN", false, "FAILED", params == null ? "{}" : params.toString());
            return ResponseEntity.status(500).body("fail");
        }
    }
}
