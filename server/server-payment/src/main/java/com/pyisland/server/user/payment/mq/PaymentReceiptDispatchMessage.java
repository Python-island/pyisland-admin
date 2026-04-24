package com.pyisland.server.user.payment.mq;

import java.time.LocalDateTime;

/**
 * 支付收据邮件异步投递消息。
 */
public record PaymentReceiptDispatchMessage(String traceId,
                                            String email,
                                            String outTradeNo,
                                            String channel,
                                            String transactionId,
                                            Integer amountFen,
                                            String currency,
                                            String productCode,
                                            LocalDateTime paidAt,
                                            LocalDateTime expireAt,
                                            String lastError) {

    public PaymentReceiptDispatchMessage(String traceId,
                                         String email,
                                         String outTradeNo,
                                         String channel,
                                         String transactionId,
                                         Integer amountFen,
                                         String currency,
                                         String productCode,
                                         LocalDateTime paidAt,
                                         LocalDateTime expireAt) {
        this(traceId, email, outTradeNo, channel, transactionId, amountFen, currency, productCode, paidAt, expireAt, null);
    }
}
