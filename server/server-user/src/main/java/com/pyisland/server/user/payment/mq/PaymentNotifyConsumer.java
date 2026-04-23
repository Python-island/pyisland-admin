package com.pyisland.server.user.payment.mq;

import com.pyisland.server.user.payment.config.PaymentMqConfig;
import com.pyisland.server.user.payment.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * 支付回调消费处理。
 */
@Component
public class PaymentNotifyConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentNotifyConsumer.class);

    private final PaymentService paymentService;
    private final RabbitTemplate rabbitTemplate;
    private final int notifyMaxRetries;

    public PaymentNotifyConsumer(PaymentService paymentService,
                                 RabbitTemplate rabbitTemplate,
                                 @Value("${payment.notify-max-retries:5}") int notifyMaxRetries) {
        this.paymentService = paymentService;
        this.rabbitTemplate = rabbitTemplate;
        this.notifyMaxRetries = Math.max(0, notifyMaxRetries);
    }

    @RabbitListener(queues = PaymentMqConfig.PAYMENT_NOTIFY_QUEUE)
    public void onMessage(PaymentNotifyMessage message,
                          @Header(name = PaymentMqConfig.PAYMENT_RETRY_HEADER, required = false) Integer retryCount) {
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
            routeToRetryOrDlq(message, retryCount, ex);
        }
    }

    @RabbitListener(queues = PaymentMqConfig.PAYMENT_NOTIFY_DLQ)
    public void onDeadLetter(PaymentNotifyMessage message,
                             @Header(name = PaymentMqConfig.PAYMENT_RETRY_HEADER, required = false) Integer retryCount) {
        if (message == null) {
            return;
        }
        log.error("payment notify entered dlq notifyId={} outTradeNo={} retryCount={}",
                message.notifyId(), message.outTradeNo(), retryCount == null ? 0 : retryCount);
    }

    private void routeToRetryOrDlq(PaymentNotifyMessage message, Integer retryCount, Exception ex) {
        int currentRetry = retryCount == null ? 0 : Math.max(0, retryCount);
        int nextRetry = currentRetry + 1;
        MessagePostProcessor setRetryHeader = m -> {
            m.getMessageProperties().setHeader(PaymentMqConfig.PAYMENT_RETRY_HEADER, nextRetry);
            return m;
        };

        if (currentRetry < notifyMaxRetries) {
            rabbitTemplate.convertAndSend(
                    PaymentMqConfig.PAYMENT_NOTIFY_EXCHANGE,
                    PaymentMqConfig.PAYMENT_NOTIFY_RETRY_ROUTING_KEY,
                    message,
                    setRetryHeader
            );
            log.warn("payment notify routed to retry notifyId={} outTradeNo={} retry={}/{} err={}",
                    message.notifyId(), message.outTradeNo(), nextRetry, notifyMaxRetries, ex.getMessage());
            return;
        }

        rabbitTemplate.convertAndSend(
                PaymentMqConfig.PAYMENT_NOTIFY_EXCHANGE,
                PaymentMqConfig.PAYMENT_NOTIFY_DLQ_ROUTING_KEY,
                message,
                setRetryHeader
        );
        log.error("payment notify routed to dlq notifyId={} outTradeNo={} retry={} err={}",
                message.notifyId(), message.outTradeNo(), nextRetry, ex.getMessage());
    }
}
