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
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 身份认证人脸素材异步上传消费者。
 * 从 RabbitMQ 消费素材消息，上传到 COS/OSS，更新数据库记录的 material_url。
 */
@Component
public class IdentityMaterialUploadConsumer {

    private static final Logger log = LoggerFactory.getLogger(IdentityMaterialUploadConsumer.class);
    private static final int MAX_RETRIES = 3;
    private static final String MATERIAL_FOLDER = "identity-material";

    private static final List<StorageProvider> TARGET_PROVIDERS = List.of(StorageProvider.COS, StorageProvider.OSS);

    private final ObjectStorageRouter objectStorageRouter;
    private final IdentityVerificationMapper verificationMapper;
    private final RabbitTemplate rabbitTemplate;

    public IdentityMaterialUploadConsumer(
            ObjectStorageRouter objectStorageRouter,
            IdentityVerificationMapper verificationMapper,
            RabbitTemplate rabbitTemplate
    ) {
        this.objectStorageRouter = objectStorageRouter;
        this.verificationMapper = verificationMapper;
        this.rabbitTemplate = rabbitTemplate;
    }

    @RabbitListener(queues = IdentityMaterialMqConfig.QUEUE)
    public void onMessage(IdentityMaterialMessage message,
                          @Header(name = IdentityMaterialMqConfig.RETRY_HEADER, required = false) Integer retryCount) {
        if (message == null || message.username() == null || message.certifyId() == null) {
            return;
        }
        try {
            String materialInfo = message.materialInfo();
            log.info("identity material consumer received username={} certifyId={} materialInfoNull={} materialInfoLen={} preview={}",
                    message.username(), message.certifyId(),
                    materialInfo == null, materialInfo == null ? 0 : materialInfo.length(),
                    materialInfo == null ? "null" : materialInfo.substring(0, Math.min(300, materialInfo.length())));
            if (materialInfo == null || materialInfo.isBlank()) {
                log.warn("identity material skipped (empty) username={} certifyId={}", message.username(), message.certifyId());
                return;
            }
            byte[] contentBytes = materialInfo.getBytes(StandardCharsets.UTF_8);
            log.info("identity material contentBytes len={} username={} certifyId={}",
                    contentBytes.length, message.username(), message.certifyId());
            String objectKey = MATERIAL_FOLDER + "/" + message.username() + "/" + message.certifyId() + ".json";
            String primaryUrl = null;
            List<String> uploadedProviders = new ArrayList<>();
            List<String> failedProviders = new ArrayList<>();

            for (StorageProvider provider : TARGET_PROVIDERS) {
                try {
                    ObjectStorageClient client = objectStorageRouter.get(provider);
                    StorageUploadResult result = client.putObject(
                            objectKey,
                            contentBytes,
                            "application/json",
                            "identity",
                            ""
                    );
                    log.info("identity material uploaded to {} username={} certifyId={} url={} size={}",
                            provider, message.username(), message.certifyId(), result.publicUrl(), result.contentLength());
                    if (primaryUrl == null) {
                        primaryUrl = result.publicUrl();
                    }
                    uploadedProviders.add(provider.name());
                } catch (Exception providerEx) {
                    log.error("identity material upload to {} failed username={} certifyId={} err={}",
                            provider, message.username(), message.certifyId(), providerEx.getMessage());
                    failedProviders.add(provider.name());
                }
            }

            if (primaryUrl == null) {
                throw new RuntimeException("所有存储提供商上传均失败: " + String.join(", ", failedProviders));
            }
            verificationMapper.updateMaterialUrl(message.certifyId(), primaryUrl, LocalDateTime.now());
            log.info("identity material upload done username={} certifyId={} primaryUrl={} uploaded={} failed={}",
                    message.username(), message.certifyId(), primaryUrl, uploadedProviders, failedProviders);
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

}
