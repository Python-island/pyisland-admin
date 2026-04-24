package com.pyisland.server.user.payment.mq;

import java.time.OffsetDateTime;

/**
 * 支付回调异步处理消息。
 */
public record PaymentNotifyMessage(String notifyId,
                                   String outTradeNo,
                                   String transactionId,
                                   String tradeState,
                                   OffsetDateTime successTime,
                                   boolean verifyOk,
                                   String rawBody,
                                   String lastError) {

    public PaymentNotifyMessage(String notifyId,
                                String outTradeNo,
                                String transactionId,
                                String tradeState,
                                OffsetDateTime successTime,
                                boolean verifyOk,
                                String rawBody) {
        this(notifyId, outTradeNo, transactionId, tradeState, successTime, verifyOk, rawBody, null);
    }
}
