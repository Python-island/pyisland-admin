package com.pyisland.server.user.payment.service;

import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.domain.AlipayTradeCancelModel;
import com.alipay.api.domain.AlipayTradeCloseModel;
import com.alipay.api.domain.AlipayTradePagePayModel;
import com.alipay.api.domain.AlipayTradeQueryModel;
import com.alipay.api.request.AlipayTradeCancelRequest;
import com.alipay.api.request.AlipayTradeCloseRequest;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.response.AlipayTradeCancelResponse;
import com.alipay.api.response.AlipayTradeCloseResponse;
import com.alipay.api.response.AlipayTradePagePayResponse;
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
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
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

    public PlaceOrderResult createPageOrder(String outTradeNo,
                                            String description,
                                            int amountFen,
                                            int timeoutMinutes) throws Exception {
        if (!isAvailable()) {
            throw new IllegalStateException("支付宝支付未启用或配置不完整");
        }
        AlipayClient client = buildClient();
        AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();
        request.setNotifyUrl(properties.getNotifyUrl());
        String returnUrl = properties.getReturnUrl();
        if (returnUrl != null && !returnUrl.isBlank()) {
            request.setReturnUrl(returnUrl);
        }

        int effectiveMinutes = Math.max(5, timeoutMinutes);
        AlipayTradePagePayModel model = new AlipayTradePagePayModel();
        model.setOutTradeNo(outTradeNo);
        model.setSubject(description);
        model.setTotalAmount(toYuan(amountFen));
        model.setProductCode("FAST_INSTANT_TRADE_PAY");
        model.setTimeoutExpress(effectiveMinutes + "m");
        // 下发绝对过期时间，防止支付宝侧在未创建交易前以旧二维码继续收款
        String timeExpire = ZonedDateTime.now(ZoneId.of("Asia/Shanghai"))
                .plusMinutes(effectiveMinutes)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        model.setTimeExpire(timeExpire);
        request.setBizModel(model);

        AlipayTradePagePayResponse response = client.pageExecute(request, "GET");
        if (!response.isSuccess()) {
            throw new IllegalStateException("支付宝下单失败: " + response.getSubCode() + " " + response.getSubMsg());
        }
        String payUrl = response.getBody();
        if (payUrl == null || payUrl.isBlank()) {
            throw new IllegalStateException("支付宝下单未返回支付链接");
        }
        return new PlaceOrderResult(null, payUrl);
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
            if (isTradeNotExist(response.getSubCode())) {
                return QueryResult.notFound();
            }
            throw new IllegalStateException("支付宝查单失败: " + response.getSubMsg());
        }
        return new QueryResult(response.getTradeStatus(), response.getTradeNo(), toOffsetDateTime(response.getSendPayDate()));
    }

    public CloseResult closeOrder(String outTradeNo) {
        if (!isAvailable()) {
            return CloseResult.failed("ALIPAY_UNAVAILABLE", "支付宝支付未启用或配置不完整");
        }
        try {
            AlipayClient client = buildClient();
            AlipayTradeCloseRequest request = new AlipayTradeCloseRequest();

            AlipayTradeCloseModel model = new AlipayTradeCloseModel();
            model.setOutTradeNo(outTradeNo);
            request.setBizModel(model);

            AlipayTradeCloseResponse response = client.execute(request);
            if (!response.isSuccess()) {
                if (isTradeNotExist(response.getSubCode())) {
                    log.info("alipay close order trade not exist outTradeNo={} subCode={} subMsg={}", outTradeNo, response.getSubCode(), response.getSubMsg());
                } else {
                    log.warn("alipay close order failed outTradeNo={} subCode={} subMsg={}", outTradeNo, response.getSubCode(), response.getSubMsg());
                }
                return CloseResult.failed(response.getSubCode(), response.getSubMsg());
            }
            return CloseResult.ok();
        } catch (Exception ex) {
            log.warn("alipay close order exception outTradeNo={} err={}", outTradeNo, ex.getMessage());
            return CloseResult.failed("ALIPAY_CLOSE_EXCEPTION", ex.getMessage());
        }
    }

    public void cancelOrder(String outTradeNo) {
        if (!isAvailable()) {
            return;
        }
        try {
            AlipayClient client = buildClient();
            AlipayTradeCancelRequest request = new AlipayTradeCancelRequest();

            AlipayTradeCancelModel model = new AlipayTradeCancelModel();
            model.setOutTradeNo(outTradeNo);
            request.setBizModel(model);

            AlipayTradeCancelResponse response = client.execute(request);
            if (!response.isSuccess()) {
                log.warn("alipay cancel order failed outTradeNo={} subCode={} subMsg={}", outTradeNo, response.getSubCode(), response.getSubMsg());
            }
        } catch (Exception ex) {
            log.warn("alipay cancel order exception outTradeNo={} err={}", outTradeNo, ex.getMessage());
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

    private boolean isTradeNotExist(String subCode) {
        return "ACQ.TRADE_NOT_EXIST".equalsIgnoreCase(subCode);
    }

    public record PlaceOrderResult(String tradeNo, String payUrl) {
    }

    public record QueryResult(String tradeStatus, String tradeNo, OffsetDateTime successTime) {
        public static QueryResult unknown() {
            return new QueryResult("UNKNOWN", null, null);
        }

        public static QueryResult notFound() {
            return new QueryResult("NOT_FOUND", null, null);
        }

        public boolean isNotFound() {
            return "NOT_FOUND".equalsIgnoreCase(tradeStatus);
        }

        public boolean success() {
            return "TRADE_SUCCESS".equalsIgnoreCase(tradeStatus) || "TRADE_FINISHED".equalsIgnoreCase(tradeStatus);
        }

        public boolean shouldClose() {
            return "TRADE_CLOSED".equalsIgnoreCase(tradeStatus);
        }
    }

    public record CloseResult(boolean success, String subCode, String subMsg) {
        public static CloseResult ok() {
            return new CloseResult(true, null, null);
        }

        public static CloseResult failed(String subCode, String subMsg) {
            return new CloseResult(false, subCode, subMsg);
        }

        public boolean tradeNotExist() {
            return "ACQ.TRADE_NOT_EXIST".equalsIgnoreCase(subCode);
        }
    }
}
