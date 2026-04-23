package com.pyisland.server.user.payment.mq;

import com.pyisland.server.user.payment.config.PaymentMqConfig;
import com.pyisland.server.user.payment.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 支付回调消费处理。
 */
@Component
public class PaymentNotifyConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentNotifyConsumer.class);

    private final PaymentService paymentService;

    public PaymentNotifyConsumer(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @RabbitListener(queues = PaymentMqConfig.PAYMENT_NOTIFY_QUEUE)
    public void onMessage(PaymentNotifyMessage message) {
        if (message == null) {
            return;
        }
        if (!message.verifyOk()) {
            log.warn("skip payment notify due to signature verify failed notifyId={} outTradeNo={}",
                    message.notifyId(), message.outTradeNo());
            return;
        }
        if (!"SUCCESS".equalsIgnoreCase(message.tradeState())) {
            return;
        }
        if (message.outTradeNo() == null || message.outTradeNo().isBlank()) {
            log.warn("skip payment notify due to empty outTradeNo notifyId={}", message.notifyId());
            return;
        }

        try {
            paymentService.completeOrderIfPending(
                    message.outTradeNo(),
                    message.transactionId(),
                    message.successTime(),
                    message.tradeState(),
                    message.rawBody()
            );
        } catch (Exception ex) {
            log.error("consume payment notify failed notifyId={} outTradeNo={} err={}",
                    message.notifyId(), message.outTradeNo(), ex.getMessage());
            throw new IllegalStateException("consume payment notify failed", ex);
        }
    }
}
