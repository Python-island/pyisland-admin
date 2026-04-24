package com.pyisland.server.user.payment.controller;

import com.pyisland.server.user.payment.config.PaymentMqConfig;
import com.pyisland.server.user.payment.mq.PaymentNotifyMessage;
import com.pyisland.server.user.payment.service.PaymentService;
import com.pyisland.server.user.payment.service.WechatPayNotifyService;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 微信支付回调。
 */
@RestController
@RequestMapping("/v1/payment/wechat")
public class WechatPayNotifyController {

    private final WechatPayNotifyService notifyService;
    private final PaymentService paymentService;
    private final RabbitTemplate rabbitTemplate;

    public WechatPayNotifyController(WechatPayNotifyService notifyService,
                                     PaymentService paymentService,
                                     RabbitTemplate rabbitTemplate) {
        this.notifyService = notifyService;
        this.paymentService = paymentService;
        this.rabbitTemplate = rabbitTemplate;
    }

    @PostMapping("/notify")
    public ResponseEntity<?> notify(@RequestBody(required = false) String body,
                                    @RequestHeader(value = "Wechatpay-Timestamp", required = false) String timestamp,
                                    @RequestHeader(value = "Wechatpay-Nonce", required = false) String nonce,
                                    @RequestHeader(value = "Wechatpay-Signature", required = false) String signature,
                                    @RequestHeader(value = "Wechatpay-Serial", required = false) String serial) {
        try {
            WechatPayNotifyService.NotifyData notifyData = notifyService.parse(body, timestamp, nonce, signature, serial);
            paymentService.logNotify(
                    notifyData.notifyId(),
                    notifyData.outTradeNo(),
                    notifyData.eventType(),
                    notifyData.verifyOk(),
                    "QUEUED",
                    notifyData.rawBody()
            );

            rabbitTemplate.convertAndSend(
                    PaymentMqConfig.PAYMENT_NOTIFY_EXCHANGE,
                    PaymentMqConfig.PAYMENT_NOTIFY_ROUTING_KEY,
                    new PaymentNotifyMessage(
                            notifyData.notifyId(),
                            notifyData.outTradeNo(),
                            notifyData.transactionId(),
                            notifyData.tradeState(),
                            notifyData.successTime(),
                            notifyData.verifyOk(),
                            notifyData.rawBody()
                    )
            );
            return ResponseEntity.ok(Map.of("code", "SUCCESS", "message", "成功"));
        } catch (Exception ex) {
            paymentService.logNotify(null, null, "UNKNOWN", false, "FAILED", body == null ? "" : body);
            return ResponseEntity.status(500).body(Map.of("code", "FAIL", "message", ex.getMessage()));
        }
    }
}
