package com.pyisland.server.upload.service;

import com.pyisland.server.upload.mapper.ObjectReplicationTaskMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;

/**
 * 对象复制任务服务。
 */
@Service
public class ObjectReplicationTaskService {

    private static final Logger log = LoggerFactory.getLogger(ObjectReplicationTaskService.class);

    private final ObjectReplicationTaskMapper objectReplicationTaskMapper;
    private final int maxRetries;

    public ObjectReplicationTaskService(ObjectReplicationTaskMapper objectReplicationTaskMapper,
                                        @Value("${object-replication.max-retries:6}") int maxRetries) {
        this.objectReplicationTaskMapper = objectReplicationTaskMapper;
        this.maxRetries = Math.max(1, maxRetries);
    }

    /**
     * 为主存储上传结果创建其余提供商的复制任务。
     * @param bizType 业务类型。
     * @param bizId 业务主键（可空）。
     * @param bizKey 业务辅助键（可空）。
     * @param fieldName 业务字段名。
     * @param sourceUploadResult 主存储上传结果。
     * @param priority 优先级（越小越高）。
     */
    public void enqueueForOtherProviders(String bizType,
                                         Long bizId,
                                         String bizKey,
                                         String fieldName,
                                         StorageUploadResult sourceUploadResult,
                                         int priority) {
        if (sourceUploadResult == null || sourceUploadResult.provider() == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        int normalizedPriority = normalizePriority(priority);
        for (StorageProvider targetProvider : StorageProvider.values()) {
            if (targetProvider == sourceUploadResult.provider()) {
                continue;
            }
            String taskKey = buildTaskKey(
                    bizType,
                    bizId,
                    bizKey,
                    fieldName,
                    sourceUploadResult.objectKey(),
                    sourceUploadResult.provider(),
                    targetProvider
            );
            int inserted = objectReplicationTaskMapper.insertIgnore(
                    taskKey,
                    safeText(bizType, 40),
                    bizId,
                    safeText(bizKey, 150),
                    safeText(fieldName, 80),
                    safeText(sourceUploadResult.objectKey(), 500),
                    sourceUploadResult.provider().name(),
                    targetProvider.name(),
                    safeText(sourceUploadResult.publicUrl(), 2000),
                    "pending",
                    normalizedPriority,
                    maxRetries,
                    now,
                    now,
                    now
            );
            if (inserted > 0) {
                log.debug("object replication task created key={} bizType={} bizId={} target={}",
                        taskKey,
                        bizType,
                        bizId,
                        targetProvider);
            }
        }
    }

    private int normalizePriority(int priority) {
        if (priority < 0) {
            return 0;
        }
        return Math.min(priority, 9);
    }

    private String safeText(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        String normalized = text.trim();
        if (normalized.length() > maxLength) {
            return normalized.substring(0, maxLength);
        }
        return normalized;
    }

    private String buildTaskKey(String bizType,
                                Long bizId,
                                String bizKey,
                                String fieldName,
                                String objectKey,
                                StorageProvider sourceProvider,
                                StorageProvider targetProvider) {
        String raw = safeText(bizType, 120)
                + "|" + (bizId == null ? "" : bizId)
                + "|" + safeText(bizKey, 200)
                + "|" + safeText(fieldName, 120)
                + "|" + safeText(objectKey, 600)
                + "|" + (sourceProvider == null ? "" : sourceProvider.name())
                + "|" + (targetProvider == null ? "" : targetProvider.name());
        return sha256Hex(raw);
    }

    private String sha256Hex(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
