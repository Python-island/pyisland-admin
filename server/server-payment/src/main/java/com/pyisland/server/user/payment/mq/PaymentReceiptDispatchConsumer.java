package com.pyisland.server.user.payment.mq;

import com.pyisland.server.user.payment.config.PaymentMqConfig;
import com.pyisland.server.user.payment.service.PaymentReceiptEmailService;
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
 * 支付收据邮件异步投递消费者。
 */
@Component
public class PaymentReceiptDispatchConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentReceiptDispatchConsumer.class);

    private final PaymentReceiptEmailService paymentReceiptEmailService;
    private final PaymentService paymentService;
    private final RabbitTemplate rabbitTemplate;
    private final int receiptMaxRetries;

    public PaymentReceiptDispatchConsumer(PaymentReceiptEmailService paymentReceiptEmailService,
                                          PaymentService paymentService,
                                          RabbitTemplate rabbitTemplate,
                                          @Value("${payment.notify-max-retries:5}") int receiptMaxRetries) {
        this.paymentReceiptEmailService = paymentReceiptEmailService;
        this.paymentService = paymentService;
        this.rabbitTemplate = rabbitTemplate;
        this.receiptMaxRetries = Math.max(0, receiptMaxRetries);
    }

    @RabbitListener(queues = PaymentMqConfig.PAYMENT_RECEIPT_QUEUE)
    public void onMessage(PaymentReceiptDispatchMessage message,
                          @Header(name = PaymentMqConfig.PAYMENT_RECEIPT_RETRY_HEADER, required = false) Integer retryCount) {
        if (message == null) {
            return;
        }
        if (message.email() == null || message.email().isBlank() || message.outTradeNo() == null || message.outTradeNo().isBlank()) {
            log.warn("skip payment receipt dispatch due to missing required fields traceId={} outTradeNo={}",
                    message.traceId(), message.outTradeNo());
            return;
        }

        try {
            paymentReceiptEmailService.sendPaymentReceipt(
                    message.email(),
                    message.outTradeNo(),
                    message.channel(),
                    message.transactionId(),
                    message.amountFen(),
                    message.currency(),
                    message.productCode(),
                    message.paidAt(),
                    message.expireAt()
            );
            log.info("payment receipt dispatched traceId={} outTradeNo={} email={}",
                    message.traceId(), message.outTradeNo(), message.email());
        } catch (Exception ex) {
            log.error("payment receipt dispatch failed traceId={} outTradeNo={} email={} err={}",
                    message.traceId(), message.outTradeNo(), message.email(), ex.getMessage());
            routeToRetryOrDlq(message, retryCount, ex);
        }
    }

    @RabbitListener(queues = PaymentMqConfig.PAYMENT_RECEIPT_DLQ)
    public void onDeadLetter(PaymentReceiptDispatchMessage message,
                             @Header(name = PaymentMqConfig.PAYMENT_RECEIPT_RETRY_HEADER, required = false) Integer retryCount) {
        if (message == null) {
            return;
        }
        int deadRetry = retryCount == null ? 0 : Math.max(0, retryCount);
        paymentService.logReceiptDlq(
                message.traceId(),
                message.outTradeNo(),
                deadRetry,
                message.lastError(),
                message.toString()
        );
        log.error("payment receipt entered dlq traceId={} outTradeNo={} email={} retryCount={} err={}",
                message.traceId(), message.outTradeNo(), message.email(), deadRetry, message.lastError());
    }

    private void routeToRetryOrDlq(PaymentReceiptDispatchMessage message, Integer retryCount, Exception ex) {
        int currentRetry = retryCount == null ? 0 : Math.max(0, retryCount);
        int nextRetry = currentRetry + 1;
        String errorMessage = ex == null ? "unknown" : ex.getMessage();
        PaymentReceiptDispatchMessage failedMessage = new PaymentReceiptDispatchMessage(
                message.traceId(),
                message.email(),
                message.outTradeNo(),
                message.channel(),
                message.transactionId(),
                message.amountFen(),
                message.currency(),
                message.productCode(),
                message.paidAt(),
                message.expireAt(),
                errorMessage
        );
        MessagePostProcessor setRetryHeader = m -> {
            m.getMessageProperties().setHeader(PaymentMqConfig.PAYMENT_RECEIPT_RETRY_HEADER, nextRetry);
            return m;
        };

        if (currentRetry < receiptMaxRetries) {
            rabbitTemplate.convertAndSend(
                    PaymentMqConfig.PAYMENT_NOTIFY_EXCHANGE,
                    PaymentMqConfig.PAYMENT_RECEIPT_RETRY_ROUTING_KEY,
                    failedMessage,
                    setRetryHeader
            );
            log.warn("payment receipt routed to retry traceId={} outTradeNo={} retry={}/{} err={}",
                    message.traceId(), message.outTradeNo(), nextRetry, receiptMaxRetries, errorMessage);
            return;
        }

        rabbitTemplate.convertAndSend(
                PaymentMqConfig.PAYMENT_NOTIFY_EXCHANGE,
                PaymentMqConfig.PAYMENT_RECEIPT_DLQ_ROUTING_KEY,
                failedMessage,
                setRetryHeader
        );
        log.error("payment receipt routed to dlq traceId={} outTradeNo={} retry={} err={}",
                message.traceId(), message.outTradeNo(), nextRetry, errorMessage);
    }
}
