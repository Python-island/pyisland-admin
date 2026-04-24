package com.pyisland.server.user.payment.service;

import com.alipay.api.internal.util.AlipaySignature;
import com.pyisland.server.user.payment.config.AlipayProperties;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * 支付宝异步通知验签与解析。
 */
@Service
public class AlipayNotifyService {

    private static final DateTimeFormatter ALIPAY_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AlipayProperties properties;

    public AlipayNotifyService(AlipayProperties properties) {
        this.properties = properties;
    }

    public NotifyData parse(Map<String, String> requestParams) throws Exception {
        Map<String, String> params = requestParams == null ? Map.of() : new HashMap<>(requestParams);
        String notifyId = trimToNull(params.get("notify_id"));
        String eventType = trimToNull(params.get("notify_type"));
        String outTradeNo = trimToNull(params.get("out_trade_no"));
        String tradeNo = trimToNull(params.get("trade_no"));
        String tradeStatus = trimToNull(params.get("trade_status"));
        OffsetDateTime successTime = parseTime(params.get("gmt_payment"));
        boolean verifyOk = verifySignature(params);
        String rawBody = params.toString();

        return new NotifyData(notifyId, eventType, outTradeNo, tradeNo, tradeStatus, successTime, verifyOk, rawBody);
    }

    private boolean verifySignature(Map<String, String> params) throws Exception {
        if (params == null || params.isEmpty()) {
            return false;
        }
        if (!properties.isConfigured()) {
            return false;
        }
        return AlipaySignature.rsaCheckV1(
                params,
                java.nio.file.Files.readString(java.nio.file.Path.of(properties.getPublicKeyPath()))
                        .replace("-----BEGIN PUBLIC KEY-----", "")
                        .replace("-----END PUBLIC KEY-----", "")
                        .replaceAll("\\s", ""),
                properties.getCharset(),
                properties.getSignType()
        );
    }

    private OffsetDateTime parseTime(String text) {
        String value = trimToNull(text);
        if (value == null) {
            return null;
        }
        try {
            return OffsetDateTime.of(java.time.LocalDateTime.parse(value, ALIPAY_TIME), ZoneOffset.ofHours(8));
        } catch (Exception ex) {
            return null;
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record NotifyData(String notifyId,
                             String eventType,
                             String outTradeNo,
                             String transactionId,
                             String tradeState,
                             OffsetDateTime successTime,
                             boolean verifyOk,
                             String rawBody) {
    }
}
