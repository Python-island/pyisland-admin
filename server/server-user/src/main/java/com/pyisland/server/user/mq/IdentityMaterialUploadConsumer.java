package com.pyisland.server.user.mq;

import com.pyisland.server.upload.service.ObjectStorageClient;
import com.pyisland.server.upload.service.ObjectStorageRouter;
import com.pyisland.server.upload.service.StorageProvider;
import com.pyisland.server.upload.service.StorageUploadResult;
import com.pyisland.server.user.config.IdentityMaterialMqConfig;
import com.pyisland.server.user.mapper.IdentityVerificationMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

/**
 * 身份认证人脸素材异步上传消费者。
 * 从 RabbitMQ 消费素材消息，上传到 COS/OSS，更新数据库记录的 material_url。
 */
@Component
public class IdentityMaterialUploadConsumer {

    private static final Logger log = LoggerFactory.getLogger(IdentityMaterialUploadConsumer.class);
    private static final int MAX_RETRIES = 3;
    private static final String MATERIAL_FOLDER = "identity-material";

    private final ObjectStorageRouter objectStorageRouter;
    private final IdentityVerificationMapper verificationMapper;
    private final RabbitTemplate rabbitTemplate;
    private final StorageProvider materialStorageProvider;

    public IdentityMaterialUploadConsumer(
            ObjectStorageRouter objectStorageRouter,
            IdentityVerificationMapper verificationMapper,
            RabbitTemplate rabbitTemplate,
            @Value("${IDENTITY_MATERIAL_STORAGE_PROVIDER:COS}") String storageProviderName
    ) {
        this.objectStorageRouter = objectStorageRouter;
        this.verificationMapper = verificationMapper;
        this.rabbitTemplate = rabbitTemplate;
        this.materialStorageProvider = parseStorageProvider(storageProviderName);
    }

    @RabbitListener(queues = IdentityMaterialMqConfig.QUEUE)
    public void onMessage(IdentityMaterialMessage message,
                          @Header(name = IdentityMaterialMqConfig.RETRY_HEADER, required = false) Integer retryCount) {
        if (message == null || message.username() == null || message.certifyId() == null) {
            return;
        }
        try {
            String materialInfo = message.materialInfo();
            log.info("identity material consumer received username={} certifyId={} materialInfoNull={} materialInfoLen={}",
                    message.username(), message.certifyId(),
                    materialInfo == null, materialInfo == null ? 0 : materialInfo.length());
            if (materialInfo == null || materialInfo.isBlank()) {
                log.warn("identity material skipped (empty) username={} certifyId={}", message.username(), message.certifyId());
                return;
            }
            ObjectStorageClient client = objectStorageRouter.get(materialStorageProvider);
            String objectKey = MATERIAL_FOLDER + "/" + message.username() + "/" + message.certifyId() + ".json";
            StorageUploadResult result = client.putObject(
                    objectKey,
                    materialInfo.getBytes(StandardCharsets.UTF_8),
                    "application/json",
                    "identity",
                    ""
            );
            String materialUrl = result.publicUrl();
            verificationMapper.updateMaterialUrl(message.certifyId(), materialUrl, LocalDateTime.now());
            log.info("identity material uploaded async username={} certifyId={} url={}",
                    message.username(), message.certifyId(), materialUrl);
        } catch (Exception ex) {
            log.error("identity material upload failed username={} certifyId={} err={}",
                    message.username(), message.certifyId(), ex.getMessage());
            routeToRetryOrDlq(message, retryCount, ex);
        }
    }

    @RabbitListener(queues = IdentityMaterialMqConfig.DLQ)
    public void onDeadLetter(IdentityMaterialMessage message,
                             @Header(name = IdentityMaterialMqConfig.RETRY_HEADER, required = false) Integer retryCount) {
        if (message == null) {
            return;
        }
        int deadRetry = retryCount == null ? 0 : Math.max(0, retryCount);
        log.error("identity material entered dlq username={} certifyId={} retryCount={} lastError={}",
                message.username(), message.certifyId(), deadRetry, message.lastError());
    }

    private void routeToRetryOrDlq(IdentityMaterialMessage message, Integer retryCount, Exception ex) {
        int currentRetry = retryCount == null ? 0 : Math.max(0, retryCount);
        int nextRetry = currentRetry + 1;
        String errorMessage = ex == null ? "unknown" : ex.getMessage();
        IdentityMaterialMessage failedMessage = new IdentityMaterialMessage(
                message.username(), message.certifyId(), message.materialInfo(), errorMessage
        );
        MessagePostProcessor setRetryHeader = m -> {
            m.getMessageProperties().setHeader(IdentityMaterialMqConfig.RETRY_HEADER, nextRetry);
            return m;
        };

        if (currentRetry < MAX_RETRIES) {
            rabbitTemplate.convertAndSend(
                    IdentityMaterialMqConfig.EXCHANGE,
                    IdentityMaterialMqConfig.RETRY_ROUTING_KEY,
                    failedMessage,
                    setRetryHeader
            );
            log.warn("identity material routed to retry username={} certifyId={} retry={}/{} err={}",
                    message.username(), message.certifyId(), nextRetry, MAX_RETRIES, errorMessage);
            return;
        }

        rabbitTemplate.convertAndSend(
                IdentityMaterialMqConfig.EXCHANGE,
                IdentityMaterialMqConfig.DLQ_ROUTING_KEY,
                failedMessage,
                setRetryHeader
        );
        log.error("identity material routed to dlq username={} certifyId={} retry={} err={}",
                message.username(), message.certifyId(), nextRetry, errorMessage);
    }

    private StorageProvider parseStorageProvider(String name) {
        if (name == null || name.isBlank()) {
            return StorageProvider.COS;
        }
        StorageProvider provider = StorageProvider.valueOf(name.trim().toUpperCase());
        if (provider != StorageProvider.COS && provider != StorageProvider.OSS) {
            throw new IllegalArgumentException("身份认证素材仅支持 COS 或 OSS 存储，当前配置: " + name);
        }
        return provider;
    }
}
