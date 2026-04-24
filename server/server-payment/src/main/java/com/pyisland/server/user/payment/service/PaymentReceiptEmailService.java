package com.pyisland.server.user.payment.service;
import com.resend.Resend;
import com.resend.services.emails.model.CreateEmailOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 支付成功收据邮件服务。
 */
@Service
public class PaymentReceiptEmailService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final String resendApiKey;
    private final String resendFrom;

    public PaymentReceiptEmailService(@Value("${resend.api-key:}") String resendApiKey,
                                      @Value("${resend.from:}") String resendFrom) {
        this.resendApiKey = resendApiKey;
        this.resendFrom = resendFrom;
    }

    public boolean isEnabled() {
        return resendApiKey != null && !resendApiKey.isBlank()
                && resendFrom != null && !resendFrom.isBlank();
    }

    public void sendPaymentReceipt(String toEmail,
                                   String outTradeNo,
                                   String channel,
                                   String transactionId,
                                   Integer amountFen,
                                   String currency,
                                   String productCode,
                                   LocalDateTime paidAt) {
        sendPaymentReceipt(toEmail, outTradeNo, channel, transactionId, amountFen, currency, productCode, paidAt, null);
    }

    public void sendPaymentReceipt(String toEmail,
                                   String outTradeNo,
                                   String channel,
                                   String transactionId,
                                   Integer amountFen,
                                   String currency,
                                   String productCode,
                                   LocalDateTime paidAt,
                                   LocalDateTime expireAt) {
        if (!isEnabled()) {
            throw new IllegalStateException("payment receipt email disabled: resend config missing");
        }
        if (toEmail == null || toEmail.isBlank()) {
            throw new IllegalArgumentException("receipt email is empty");
        }
        if (outTradeNo == null || outTradeNo.isBlank()) {
            throw new IllegalArgumentException("outTradeNo is empty");
        }

        String safeChannel = channel == null || channel.isBlank() ? "UNKNOWN" : channel;
        String paidTimeText = paidAt == null ? "-" : paidAt.format(TIME_FORMATTER);
        String expireTimeText = expireAt == null ? "-" : expireAt.format(TIME_FORMATTER);
        String amountText = formatAmountYuan(amountFen);
        String safeTransactionId = transactionId == null || transactionId.isBlank() ? "-" : transactionId;

        String subject = "eIsland 支付收据 - " + outTradeNo;
        String html = """
                <div style=\"font-family:Arial,sans-serif;line-height:1.7;color:#111\">
                  <h2 style=\"margin:0 0 12px 0\">eIsland 支付成功收据</h2>
                  <p>你的订单已支付成功，以下为收据信息：</p>
                  <table style=\"border-collapse:collapse;margin-top:12px\">
                    <tr><td style=\"padding:6px 12px 6px 0;color:#666\">订单号</td><td style=\"padding:6px 0\"><strong>%s</strong></td></tr>
                    <tr><td style=\"padding:6px 12px 6px 0;color:#666\">支付渠道</td><td style=\"padding:6px 0\">%s</td></tr>
                    <tr><td style=\"padding:6px 12px 6px 0;color:#666\">交易号</td><td style=\"padding:6px 0\">%s</td></tr>
                    <tr><td style=\"padding:6px 12px 6px 0;color:#666\">支付金额</td><td style=\"padding:6px 0\">%s %s</td></tr>
                    <tr><td style=\"padding:6px 12px 6px 0;color:#666\">支付时间</td><td style=\"padding:6px 0\">%s</td></tr>
                    <tr><td style=\"padding:6px 12px 6px 0;color:#666\">订单到期时间</td><td style=\"padding:6px 0\">%s</td></tr>
                    <tr><td style=\"padding:6px 12px 6px 0;color:#666\">商品</td><td style=\"padding:6px 0\">%s</td></tr>
                  </table>
                  <p style=\"margin-top:12px;color:#333\">获取发票请在我的订单内操作。</p>
                  <p style=\"margin-top:16px;color:#666\">如非本人操作，请尽快联系客服处理。</p>
                </div>
                """.formatted(
                outTradeNo,
                safeChannel,
                safeTransactionId,
                amountText,
                currency == null || currency.isBlank() ? "CNY" : currency,
                paidTimeText,
                expireTimeText,
                productCode == null ? "-" : productCode
        );

        try {
            Resend resend = new Resend(resendApiKey);
            CreateEmailOptions options = CreateEmailOptions.builder()
                    .from(resendFrom)
                    .to(List.of(toEmail.trim()))
                    .subject(subject)
                    .html(html)
                    .build();
            resend.emails().send(options);
        } catch (Exception ex) {
            throw new IllegalStateException("send payment receipt failed", ex);
        }
    }

    private String formatAmountYuan(Integer amountFen) {
        if (amountFen == null) {
            return "0.00";
        }
        return BigDecimal.valueOf(amountFen)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                .toPlainString();
    }
}
