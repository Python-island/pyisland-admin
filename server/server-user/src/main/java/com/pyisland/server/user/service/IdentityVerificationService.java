package com.pyisland.server.user.service;

import com.pyisland.server.user.config.IdentityMaterialMqConfig;
import com.pyisland.server.user.entity.IdentityVerification;
import com.pyisland.server.user.mapper.IdentityVerificationMapper;
import com.pyisland.server.user.mq.IdentityMaterialMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 身份认证业务服务。
 * <ul>
 *     <li>调用 AlipayIdentityClient 完成初始化 / 认证 / 查询</li>
 *     <li>AES-256-GCM 加密存储姓名与身份证号</li>
 *     <li>人脸素材信息存储到 COS/OSS 对象存储</li>
 * </ul>
 */
@Service
public class IdentityVerificationService {

    private static final Logger log = LoggerFactory.getLogger(IdentityVerificationService.class);
    private static final int AES_GCM_IV_LENGTH = 12;
    private static final int AES_GCM_TAG_BITS = 128;
    private static final DateTimeFormatter ORDER_NO_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final String REDIS_RATE_PREFIX = "identity:rate:";
    private static final String REDIS_VERIFIED_PREFIX = "identity:verified:";
    private static final long RATE_LIMIT_WINDOW_SECONDS = 300;
    private static final long RATE_LIMIT_MAX = 3;
    private static final long VERIFIED_CACHE_SECONDS = 3600;

    private final AlipayIdentityClient alipayIdentityClient;
    private final IdentityVerificationMapper verificationMapper;
    private final RabbitTemplate rabbitTemplate;
    private final StringRedisTemplate redisTemplate;
    private final SecretKeySpec aesKeySpec;
    private final SecureRandom secureRandom = new SecureRandom();

    public IdentityVerificationService(
            AlipayIdentityClient alipayIdentityClient,
            IdentityVerificationMapper verificationMapper,
            RabbitTemplate rabbitTemplate,
            @Qualifier("identityRedisTemplate") StringRedisTemplate redisTemplate,
            @Value("${IDENTITY_AES_KEY_BASE64:}") String aesKeyBase64
    ) {
        this.alipayIdentityClient = alipayIdentityClient;
        this.verificationMapper = verificationMapper;
        this.rabbitTemplate = rabbitTemplate;
        this.redisTemplate = redisTemplate;
        this.aesKeySpec = buildAesKey(aesKeyBase64);
    }

    /**
     * 发起身份认证：初始化 + 获取认证 URL，一步返回前端。
     *
     * @param username 当前登录用户名。
     * @param certName 真实姓名。
     * @param certNo   身份证号。
     * @return 认证 URL，前端跳转到该地址。
     */
    @Transactional
    public StartResult startVerification(String username, String certName, String certNo) throws Exception {
        if (!alipayIdentityClient.isAvailable()) {
            throw new IllegalStateException("身份认证服务未启用");
        }
        if (aesKeySpec == null) {
            throw new IllegalStateException("身份认证加密密钥未配置");
        }

        checkRateLimit(username);

        IdentityVerification existing = verificationMapper.selectLatestPassedByUsername(username);
        if (existing != null) {
            throw new IllegalStateException("该用户已通过身份认证，无需重复认证");
        }

        String outerOrderNo = generateOuterOrderNo(username);
        String encCertName = encrypt(certName);
        String encCertNo = encrypt(certNo);

        AlipayIdentityClient.InitializeResult initResult = alipayIdentityClient.initialize(outerOrderNo, certName, certNo);
        String certifyId = initResult.certifyId();

        AlipayIdentityClient.CertifyResult certifyResult = alipayIdentityClient.certify(certifyId);
        String certifyUrl = certifyResult.certifyUrl();

        LocalDateTime now = LocalDateTime.now();
        IdentityVerification record = new IdentityVerification();
        record.setUsername(username);
        record.setOuterOrderNo(outerOrderNo);
        record.setCertifyId(certifyId);
        record.setCertNameCiphertext(encCertName);
        record.setCertNoCiphertext(encCertNo);
        record.setStatus(IdentityVerification.STATUS_CERTIFYING);
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        verificationMapper.insert(record);

        log.info("identity verification started username={} outerOrderNo={} certifyId={}", username, outerOrderNo, certifyId);
        incrementRateLimit(username);
        return new StartResult(certifyId, certifyUrl, outerOrderNo);
    }

    /**
     * 查询认证结果并更新状态。
     *
     * @param username  当前登录用户名。
     * @param certifyId certify_id。
     * @return 查询结果。
     */
    @Transactional
    public VerifyResult queryAndUpdate(String username, String certifyId) throws Exception {
        IdentityVerification record = verificationMapper.selectByCertifyId(certifyId);
        if (record == null) {
            throw new IllegalArgumentException("认证记录不存在");
        }
        if (!record.getUsername().equals(username)) {
            throw new IllegalArgumentException("无权查询此认证记录");
        }
        if (IdentityVerification.STATUS_PASSED.equals(record.getStatus())) {
            return new VerifyResult(true, "已通过认证");
        }

        AlipayIdentityClient.QueryResult queryResult = alipayIdentityClient.query(certifyId);

        if (queryResult.passed() && queryResult.materialInfo() != null && !queryResult.materialInfo().isBlank()) {
            String rawMaterial = queryResult.materialInfo().trim();
            log.info("identity materialInfo from alipay username={} certifyId={} len={} preview={}",
                    username, certifyId, rawMaterial.length(),
                    rawMaterial.substring(0, Math.min(200, rawMaterial.length())));
            if ("{}".equals(rawMaterial) || "[]".equals(rawMaterial) || "null".equals(rawMaterial)) {
                log.warn("identity materialInfo is empty object from alipay username={} certifyId={} raw={} (可能未开通「实人认证留底照」能力)",
                        username, certifyId, rawMaterial);
            } else {
                publishMaterialUpload(username, certifyId, rawMaterial);
            }
        } else if (queryResult.passed()) {
            log.warn("identity materialInfo is null/blank from alipay username={} certifyId={} (可能未开通「实人认证留底照」能力)",
                    username, certifyId);
        }

        String newStatus = queryResult.passed() ? IdentityVerification.STATUS_PASSED : IdentityVerification.STATUS_FAILED;
        verificationMapper.updateStatus(certifyId, newStatus, null, LocalDateTime.now());

        if (queryResult.passed()) {
            cacheVerifiedStatus(username, true);
        }

        log.info("identity verification query username={} certifyId={} passed={}", username, certifyId, queryResult.passed());
        return new VerifyResult(queryResult.passed(), queryResult.passed() ? "认证通过" : "认证未通过: " + queryResult.subMsg());
    }

    /**
     * 查询用户是否已通过实名认证。
     */
    public boolean isVerified(String username) {
        String cached = redisTemplate.opsForValue().get(REDIS_VERIFIED_PREFIX + username);
        if (cached != null) {
            return "1".equals(cached);
        }
        IdentityVerification record = verificationMapper.selectLatestPassedByUsername(username);
        boolean verified = record != null;
        cacheVerifiedStatus(username, verified);
        return verified;
    }

    /**
     * 查询用户认证记录列表。
     */
    public List<IdentityVerification> listRecords(String username, int limit) {
        return verificationMapper.selectByUsername(username, Math.max(1, Math.min(limit, 50)));
    }

    /**
     * 管理员查询用户实名信息（解密姓名，脱敏身份证号）。
     * @param username 用户名。
     * @return 实名信息；未认证返回 null。
     */
    public IdentityInfo getIdentityInfo(String username) {
        IdentityVerification record = verificationMapper.selectLatestPassedByUsername(username);
        if (record == null) {
            return null;
        }
        String certName = decrypt(record.getCertNameCiphertext());
        String certNo = decrypt(record.getCertNoCiphertext());
        String maskedCertNo = maskCertNo(certNo);
        return new IdentityInfo(certName, maskedCertNo, record.getStatus(), record.getCreatedAt(), record.getUpdatedAt());
    }

    private String maskCertNo(String certNo) {
        if (certNo == null || certNo.length() < 8) {
            return certNo;
        }
        return certNo.substring(0, 4) + "**********" + certNo.substring(certNo.length() - 4);
    }

    public record IdentityInfo(String certName, String maskedCertNo, String status,
                                java.time.LocalDateTime verifiedAt, java.time.LocalDateTime updatedAt) {
    }

    private void publishMaterialUpload(String username, String certifyId, String materialInfo) {
        try {
            IdentityMaterialMessage message = new IdentityMaterialMessage(username, certifyId, materialInfo);
            rabbitTemplate.convertAndSend(
                    IdentityMaterialMqConfig.EXCHANGE,
                    IdentityMaterialMqConfig.ROUTING_KEY,
                    message
            );
            log.info("identity material upload published username={} certifyId={} materialInfoLen={}",
                    username, certifyId, materialInfo == null ? 0 : materialInfo.length());
        } catch (Exception ex) {
            log.warn("identity material publish failed username={} certifyId={} err={}", username, certifyId, ex.getMessage());
        }
    }

    // ── Redis 频率限制与缓存 ──

    private void checkRateLimit(String username) {
        String key = REDIS_RATE_PREFIX + username;
        String val = redisTemplate.opsForValue().get(key);
        if (val != null) {
            long count = Long.parseLong(val);
            if (count >= RATE_LIMIT_MAX) {
                throw new IllegalStateException("操作过于频繁，请 " + RATE_LIMIT_WINDOW_SECONDS / 60 + " 分钟后再试");
            }
        }
    }

    private void incrementRateLimit(String username) {
        String key = REDIS_RATE_PREFIX + username;
        Long current = redisTemplate.opsForValue().increment(key);
        if (current != null && current == 1L) {
            redisTemplate.expire(key, RATE_LIMIT_WINDOW_SECONDS, TimeUnit.SECONDS);
        }
    }

    private void cacheVerifiedStatus(String username, boolean verified) {
        redisTemplate.opsForValue().set(
                REDIS_VERIFIED_PREFIX + username,
                verified ? "1" : "0",
                VERIFIED_CACHE_SECONDS,
                TimeUnit.SECONDS
        );
    }

    private String generateOuterOrderNo(String username) {
        String ts = LocalDateTime.now().format(ORDER_NO_FMT);
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return "IDV" + ts + suffix;
    }

    // ── AES-256-GCM 加解密 ──

    private String encrypt(String plaintext) {
        if (aesKeySpec == null || plaintext == null || plaintext.isBlank()) {
            return null;
        }
        try {
            byte[] iv = new byte[AES_GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, aesKeySpec, new GCMParameterSpec(AES_GCM_TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception ex) {
            throw new RuntimeException("身份信息加密失败", ex);
        }
    }

    private String decrypt(String base64Ciphertext) {
        if (aesKeySpec == null || base64Ciphertext == null || base64Ciphertext.isBlank()) {
            return null;
        }
        try {
            byte[] combined = Base64.getDecoder().decode(base64Ciphertext);
            byte[] iv = new byte[AES_GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, iv.length);
            byte[] ciphertext = new byte[combined.length - iv.length];
            System.arraycopy(combined, iv.length, ciphertext, 0, ciphertext.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, aesKeySpec, new GCMParameterSpec(AES_GCM_TAG_BITS, iv));
            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new RuntimeException("身份信息解密失败", ex);
        }
    }

    private SecretKeySpec buildAesKey(String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            log.warn("IDENTITY_AES_KEY_BASE64 未配置，身份认证加密不可用");
            return null;
        }
        byte[] keyBytes = Base64.getDecoder().decode(base64Key.trim());
        if (keyBytes.length != 32) {
            log.warn("IDENTITY_AES_KEY_BASE64 长度不正确（期望 32 字节 / 256 位），实际 {} 字节", keyBytes.length);
            return null;
        }
        return new SecretKeySpec(keyBytes, "AES");
    }

    public record StartResult(String certifyId, String certifyUrl, String outerOrderNo) {
    }

    public record VerifyResult(boolean passed, String message) {
    }
}
