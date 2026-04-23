package com.pyisland.server.user.payment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pyisland.server.user.payment.config.WechatPayProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Base64;

/**
 * 微信支付回调解析服务。
 */
@Service
public class WechatPayNotifyService {

    private static final Logger log = LoggerFactory.getLogger(WechatPayNotifyService.class);

    private final ObjectMapper objectMapper;
    private final WechatPayProperties properties;

    public WechatPayNotifyService(ObjectProvider<ObjectMapper> objectMapperProvider,
                                  WechatPayProperties properties) {
        this.objectMapper = objectMapperProvider.getIfAvailable(ObjectMapper::new);
        this.properties = properties;
    }

    public NotifyData parse(String body,
                            String timestamp,
                            String nonce,
                            String signature,
                            String serial) throws Exception {
        JsonNode root = objectMapper.readTree(body == null ? "{}" : body);
        String notifyId = text(root, "id");
        String eventType = text(root, "event_type");

        boolean verifyOk = verifySignature(body == null ? "" : body, timestamp, nonce, signature, serial);

        JsonNode transaction = root;
        JsonNode resource = root.path("resource");
        if (!resource.isMissingNode() && resource.hasNonNull("ciphertext")) {
            String nonceRaw = text(resource, "nonce");
            String associatedData = text(resource, "associated_data");
            String ciphertext = text(resource, "ciphertext");
            String plain = decryptResource(associatedData, nonceRaw, ciphertext);
            transaction = objectMapper.readTree(plain);
        }

        String outTradeNo = text(transaction, "out_trade_no");
        String transactionId = text(transaction, "transaction_id");
        String tradeState = text(transaction, "trade_state");
        OffsetDateTime successTime = parseTime(text(transaction, "success_time"));

        return new NotifyData(
                notifyId,
                eventType,
                outTradeNo,
                transactionId,
                tradeState,
                successTime,
                verifyOk,
                body == null ? "" : body
        );
    }

    private boolean verifySignature(String body,
                                    String timestamp,
                                    String nonce,
                                    String signature,
                                    String serial) {
        if (signature == null || signature.isBlank() || timestamp == null || nonce == null) {
            return false;
        }
        String certPath = properties.getPlatformCertPath();
        if (certPath == null || certPath.isBlank()) {
            return true;
        }
        try {
            byte[] certBytes = Files.readAllBytes(Path.of(certPath));
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            X509Certificate cert = (X509Certificate) factory.generateCertificate(new java.io.ByteArrayInputStream(certBytes));
            String serialHex = cert.getSerialNumber().toString(16).toUpperCase();
            if (serial != null && !serial.isBlank() && !serialHex.equalsIgnoreCase(serial)) {
                log.warn("wechat notify serial mismatch header={} cert={}", serial, serialHex);
            }
            String message = timestamp + "\n" + nonce + "\n" + body + "\n";
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(cert.getPublicKey());
            verifier.update(message.getBytes(StandardCharsets.UTF_8));
            return verifier.verify(Base64.getDecoder().decode(signature));
        } catch (Exception ex) {
            log.warn("wechat notify verify failed err={}", ex.getMessage());
            return false;
        }
    }

    private String decryptResource(String associatedData, String nonce, String ciphertext) throws Exception {
        String key = properties.getApiV3Key();
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("APIv3 key 未配置");
        }
        SecretKeySpec keySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec gcmSpec = new GCMParameterSpec(128, nonce.getBytes(StandardCharsets.UTF_8));
        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);
        if (associatedData != null && !associatedData.isBlank()) {
            cipher.updateAAD(associatedData.getBytes(StandardCharsets.UTF_8));
        }
        byte[] plain = cipher.doFinal(Base64.getDecoder().decode(ciphertext));
        return new String(plain, StandardCharsets.UTF_8);
    }

    private OffsetDateTime parseTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isMissingNode() || v.isNull() ? null : v.asText(null);
    }

    public record NotifyData(String notifyId,
                             String eventType,
                             String outTradeNo,
                             String transactionId,
                             String tradeState,
                             OffsetDateTime successTime,
                             boolean verifyOk,
                             String rawBody) {
        public boolean success() {
            return "SUCCESS".equalsIgnoreCase(tradeState);
        }
    }
}
