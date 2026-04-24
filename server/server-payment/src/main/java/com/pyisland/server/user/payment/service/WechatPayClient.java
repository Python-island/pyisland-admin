package com.pyisland.server.user.payment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pyisland.server.user.payment.config.WechatPayProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

/**
 * 微信支付 v3 客户端。
 */
@Component
public class WechatPayClient {

    private static final Logger log = LoggerFactory.getLogger(WechatPayClient.class);
    private static final String WECHAT_API_BASE = "https://api.mch.weixin.qq.com";

    private final WechatPayProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public WechatPayClient(WechatPayProperties properties,
                           ObjectProvider<ObjectMapper> objectMapperProvider) {
        this.properties = properties;
        this.objectMapper = objectMapperProvider.getIfAvailable(ObjectMapper::new);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .build();
    }

    public boolean isAvailable() {
        return properties.isConfigured();
    }

    public PlaceOrderResult createNativeOrder(String outTradeNo,
                                              String description,
                                              int amountFen) throws Exception {
        if (!isAvailable()) {
            throw new IllegalStateException("微信支付未启用或配置不完整");
        }
        String path = "/v3/pay/transactions/native";
        String body = objectMapper.writeValueAsString(Map.of(
                "mchid", properties.getMchId(),
                "appid", properties.getAppId(),
                "description", description,
                "notify_url", properties.getNotifyUrl(),
                "out_trade_no", outTradeNo,
                "amount", Map.of("total", amountFen, "currency", "CNY")
        ));
        JsonNode node = requestJson("POST", path, body);
        String codeUrl = node.path("code_url").asText("");
        String prepayId = node.path("prepay_id").asText(null);
        if (codeUrl.isBlank()) {
            throw new IllegalStateException("微信下单未返回 code_url");
        }
        return new PlaceOrderResult(prepayId, codeUrl);
    }

    public QueryResult queryOrder(String outTradeNo) throws Exception {
        if (!isAvailable()) {
            return QueryResult.unknown();
        }
        String encoded = URLEncoder.encode(outTradeNo, StandardCharsets.UTF_8);
        String path = "/v3/pay/transactions/out-trade-no/" + encoded + "?mchid="
                + URLEncoder.encode(properties.getMchId(), StandardCharsets.UTF_8);
        JsonNode node = requestJson("GET", path, "");
        String tradeState = node.path("trade_state").asText("");
        String transactionId = node.path("transaction_id").asText(null);
        String successTimeRaw = node.path("success_time").asText(null);
        return new QueryResult(tradeState, transactionId, parseOffsetDateTime(successTimeRaw));
    }

    public void closeOrder(String outTradeNo) {
        if (!isAvailable()) {
            return;
        }
        try {
            String encoded = URLEncoder.encode(outTradeNo, StandardCharsets.UTF_8);
            String path = "/v3/pay/transactions/out-trade-no/" + encoded + "/close";
            String body = objectMapper.writeValueAsString(Map.of("mchid", properties.getMchId()));
            requestJson("POST", path, body);
        } catch (Exception ex) {
            log.warn("wechat close order failed outTradeNo={} err={}", outTradeNo, ex.getMessage());
        }
    }

    private JsonNode requestJson(String method, String pathWithQuery, String body) throws Exception {
        String nonce = randomNonce();
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String payload = body == null ? "" : body;
        String signMessage = method + "\n"
                + pathWithQuery + "\n"
                + timestamp + "\n"
                + nonce + "\n"
                + payload + "\n";
        String signature = sign(signMessage, loadPrivateKey());
        String authorization = "WECHATPAY2-SHA256-RSA2048 "
                + "mchid=\"" + properties.getMchId() + "\"," 
                + "nonce_str=\"" + nonce + "\"," 
                + "timestamp=\"" + timestamp + "\"," 
                + "serial_no=\"" + properties.getSerialNo() + "\"," 
                + "signature=\"" + signature + "\"";

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(WECHAT_API_BASE + pathWithQuery))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .header("Authorization", authorization)
                .header("User-Agent", "eIsland-server/1.0");

        if ("GET".equalsIgnoreCase(method)) {
            builder.GET();
        } else {
            builder.header("Content-Type", "application/json");
            builder.method(method, HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8));
        }

        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        String respBody = response.body() == null ? "" : response.body();
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("微信支付请求失败: HTTP " + response.statusCode() + " body=" + respBody);
        }
        if (respBody.isBlank()) {
            return objectMapper.createObjectNode();
        }
        return objectMapper.readTree(respBody);
    }

    private OffsetDateTime parseOffsetDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private String randomNonce() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private PrivateKey loadPrivateKey() throws Exception {
        String path = properties.getPrivateKeyPath();
        if (path == null || path.isBlank()) {
            throw new IllegalStateException("微信支付私钥路径未配置");
        }
        String pem = Files.readString(Path.of(path), StandardCharsets.UTF_8)
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] bytes = Base64.getDecoder().decode(pem);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(bytes);
        return KeyFactory.getInstance("RSA").generatePrivate(spec);
    }

    private String sign(String message, PrivateKey privateKey) throws Exception {
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(privateKey);
        signature.update(message.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signature.sign());
    }

    public record PlaceOrderResult(String prepayId, String codeUrl) {
    }

    public record QueryResult(String tradeState, String transactionId, OffsetDateTime successTime) {
        public static QueryResult unknown() {
            return new QueryResult("UNKNOWN", null, null);
        }

        public boolean success() {
            return "SUCCESS".equalsIgnoreCase(tradeState);
        }

        public boolean shouldClose() {
            return "CLOSED".equalsIgnoreCase(tradeState)
                    || "REVOKED".equalsIgnoreCase(tradeState)
                    || "PAYERROR".equalsIgnoreCase(tradeState);
        }

        public ZoneOffset zoneOffsetOrDefault() {
            return successTime == null ? ZoneOffset.UTC : successTime.getOffset();
        }
    }
}
