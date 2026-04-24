package com.pyisland.server.user.payment.service;

import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.domain.AlipayTradeCloseModel;
import com.alipay.api.domain.AlipayTradePrecreateModel;
import com.alipay.api.domain.AlipayTradeQueryModel;
import com.alipay.api.request.AlipayTradeCloseRequest;
import com.alipay.api.request.AlipayTradePrecreateRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.response.AlipayTradeCloseResponse;
import com.alipay.api.response.AlipayTradePrecreateResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.pyisland.server.user.payment.config.AlipayProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Date;

/**
 * 支付宝官方 SDK 客户端封装。
 */
@Component
public class AlipaySdkClient {

    private static final Logger log = LoggerFactory.getLogger(AlipaySdkClient.class);

    private final AlipayProperties properties;

    public AlipaySdkClient(AlipayProperties properties) {
        this.properties = properties;
    }

    public boolean isAvailable() {
        return properties.isConfigured();
    }

    public PlaceOrderResult createPreOrder(String outTradeNo,
                                           String description,
                                           int amountFen) throws Exception {
        if (!isAvailable()) {
            throw new IllegalStateException("支付宝支付未启用或配置不完整");
        }
        AlipayClient client = buildClient();
        AlipayTradePrecreateRequest request = new AlipayTradePrecreateRequest();
        request.setNotifyUrl(properties.getNotifyUrl());

        AlipayTradePrecreateModel model = new AlipayTradePrecreateModel();
        model.setOutTradeNo(outTradeNo);
        model.setSubject(description);
        model.setTotalAmount(toYuan(amountFen));
        model.setTimeoutExpress("15m");
        request.setBizModel(model);

        AlipayTradePrecreateResponse response = client.execute(request);
        if (!response.isSuccess()) {
            throw new IllegalStateException("支付宝下单失败: " + response.getSubMsg());
        }
        String qrCode = response.getQrCode();
        if (qrCode == null || qrCode.isBlank()) {
            throw new IllegalStateException("支付宝下单未返回二维码");
        }
        return new PlaceOrderResult(null, qrCode);
    }

    public QueryResult queryOrder(String outTradeNo) throws Exception {
        if (!isAvailable()) {
            return QueryResult.unknown();
        }
        AlipayClient client = buildClient();
        AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();

        AlipayTradeQueryModel model = new AlipayTradeQueryModel();
        model.setOutTradeNo(outTradeNo);
        request.setBizModel(model);

        AlipayTradeQueryResponse response = client.execute(request);
        if (!response.isSuccess()) {
            throw new IllegalStateException("支付宝查单失败: " + response.getSubMsg());
        }
        return new QueryResult(response.getTradeStatus(), response.getTradeNo(), toOffsetDateTime(response.getSendPayDate()));
    }

    public void closeOrder(String outTradeNo) {
        if (!isAvailable()) {
            return;
        }
        try {
            AlipayClient client = buildClient();
            AlipayTradeCloseRequest request = new AlipayTradeCloseRequest();

            AlipayTradeCloseModel model = new AlipayTradeCloseModel();
            model.setOutTradeNo(outTradeNo);
            request.setBizModel(model);

            AlipayTradeCloseResponse response = client.execute(request);
            if (!response.isSuccess()) {
                log.warn("alipay close order failed outTradeNo={} subCode={} subMsg={}", outTradeNo, response.getSubCode(), response.getSubMsg());
            }
        } catch (Exception ex) {
            log.warn("alipay close order exception outTradeNo={} err={}", outTradeNo, ex.getMessage());
        }
    }

    private AlipayClient buildClient() throws Exception {
        String privateKey = readKey(properties.getPrivateKeyPath());
        String publicKey = readKey(properties.getPublicKeyPath());
        return new DefaultAlipayClient(
                properties.getGatewayUrl(),
                properties.getAppId(),
                privateKey,
                "json",
                properties.getCharset(),
                publicKey,
                properties.getSignType()
        );
    }

    private String readKey(String path) throws Exception {
        if (path == null || path.isBlank()) {
            throw new IllegalStateException("支付宝密钥路径未配置");
        }
        return Files.readString(Path.of(path), StandardCharsets.UTF_8)
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
    }

    private String toYuan(int fen) {
        BigDecimal yuan = BigDecimal.valueOf(fen).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        return yuan.toPlainString();
    }

    private OffsetDateTime toOffsetDateTime(Date date) {
        if (date == null) {
            return null;
        }
        return date.toInstant().atOffset(ZoneOffset.ofHours(8));
    }

    public record PlaceOrderResult(String tradeNo, String qrCode) {
    }

    public record QueryResult(String tradeStatus, String tradeNo, OffsetDateTime successTime) {
        public static QueryResult unknown() {
            return new QueryResult("UNKNOWN", null, null);
        }

        public boolean success() {
            return "TRADE_SUCCESS".equalsIgnoreCase(tradeStatus) || "TRADE_FINISHED".equalsIgnoreCase(tradeStatus);
        }

        public boolean shouldClose() {
            return "TRADE_CLOSED".equalsIgnoreCase(tradeStatus);
        }
    }
}
