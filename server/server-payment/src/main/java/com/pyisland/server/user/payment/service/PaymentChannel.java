package com.pyisland.server.user.payment.service;

/**
 * 支付通道。
 */
public enum PaymentChannel {
    WECHAT,
    ALIPAY;

    public static PaymentChannel from(String value) {
        if (value == null || value.isBlank()) {
            return WECHAT;
        }
        try {
            return PaymentChannel.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return WECHAT;
        }
    }
}
