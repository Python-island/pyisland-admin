package com.pyisland.server.upload.service;

import com.pyisland.server.upload.mapper.ObjectOutboxMapper;
import com.pyisland.server.upload.mapper.ObjectReplicationTaskMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Map;

/**
 * 对象复制任务服务。
 */
@Service
public class ObjectReplicationTaskService {

    private static final Logger log = LoggerFactory.getLogger(ObjectReplicationTaskService.class);
    public static final String OUTBOX_EVENT_TYPE_REPLICATION_PUBLISH = "object.replication.publish";

    private final ObjectOutboxMapper objectOutboxMapper;
    private final ObjectReplicationTaskMapper objectReplicationTaskMapper;
    private final ObjectStorageRouter objectStorageRouter;
    private final boolean replicationEnabled;
    private final int maxRetries;
    private final int sourceFetchTimeoutMs;

    public ObjectReplicationTaskService(ObjectOutboxMapper objectOutboxMapper,
                                        ObjectReplicationTaskMapper objectReplicationTaskMapper,
                                        ObjectStorageRouter objectStorageRouter,
                                        @Value("${object-replication.enabled:true}") boolean replicationEnabled,
                                        @Value("${object-replication.max-retries:6}") int maxRetries,
                                        @Value("${object-replication.source-fetch-timeout-ms:60000}") int sourceFetchTimeoutMs) {
        this.objectOutboxMapper = objectOutboxMapper;
        this.objectReplicationTaskMapper = objectReplicationTaskMapper;
        this.objectStorageRouter = objectStorageRouter;
        this.replicationEnabled = replicationEnabled;
        this.maxRetries = Math.max(1, maxRetries);
        this.sourceFetchTimeoutMs = Math.max(3000, sourceFetchTimeoutMs);
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
    @Transactional(rollbackFor = Exception.class)
    public void enqueueForOtherProviders(String bizType,
                                         Long bizId,
                                         String bizKey,
                                         String fieldName,
                                         StorageUploadResult sourceUploadResult,
                                         int priority) {
        if (!replicationEnabled) {
            return;
        }
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
                Long taskId = objectReplicationTaskMapper.selectIdByTaskKey(taskKey);
                createOutboxEvent(taskId, normalizedPriority, now);
                log.debug("object replication task created key={} bizType={} bizId={} target={}",
                        taskKey,
                        bizType,
                        bizId,
                        targetProvider);
            }
        }
    }

    public ProcessResult processTask(Long taskId, String traceId, int attemptNo) {
        long startedAt = System.currentTimeMillis();
        if (taskId == null || taskId <= 0) {
            return new ProcessResult(true, "", "", 0, maxRetries);
        }
        Map<String, Object> taskRow = objectReplicationTaskMapper.selectById(taskId);
        if (taskRow == null || taskRow.isEmpty()) {
            return new ProcessResult(true, "", "", 0, maxRetries);
        }
        String status = safeText(stringValue(taskRow.get("status")), 20).toLowerCase();
        int taskMaxRetries = Math.max(1, parseInt(taskRow.get("maxRetries"), maxRetries));
        if ("success".equals(status) || "dlq".equals(status)) {
            return new ProcessResult(true,
                    safeText(stringValue(taskRow.get("targetUrl")), 2000),
                    "",
                    elapsedMs(startedAt),
                    taskMaxRetries);
        }

        String sourceUrl = safeText(stringValue(taskRow.get("sourceUrl")), 2000);
        String objectKey = safeText(stringValue(taskRow.get("objectKey")), 500);
        String targetProviderRaw = safeText(stringValue(taskRow.get("targetProvider")), 20);
        LocalDateTime now = LocalDateTime.now();
        try {
            StorageProvider targetProvider = StorageProvider.valueOf(targetProviderRaw.toUpperCase());
            SourcePayload payload = fetchSourcePayload(sourceUrl);
            StorageUploadResult replicated = objectStorageRouter
                    .get(targetProvider)
                    .putObject(objectKey, payload.content(), payload.contentType());
            int durationMs = elapsedMs(startedAt);
            objectReplicationTaskMapper.markSuccess(taskId,
                    safeText(replicated.publicUrl(), 2000),
                    now,
                    now);
            objectReplicationTaskMapper.insertLog(taskId,
                    safeText(traceId, 100),
                    Math.max(1, attemptNo),
                    "success",
                    durationMs,
                    "",
                    now);
            return new ProcessResult(true,
                    safeText(replicated.publicUrl(), 2000),
                    "",
                    durationMs,
                    taskMaxRetries);
        } catch (Exception ex) {
            int durationMs = elapsedMs(startedAt);
            String error = safeText(ex.getMessage(), 500);
            objectReplicationTaskMapper.insertLog(taskId,
                    safeText(traceId, 100),
                    Math.max(1, attemptNo),
                    "failed",
                    durationMs,
                    error,
                    now);
            return new ProcessResult(false, "", error, durationMs, taskMaxRetries);
        }
    }

    public void markRetrying(Long taskId, int retryCount, LocalDateTime nextRetryAt, String errorMessage) {
        if (taskId == null || taskId <= 0) {
            return;
        }
        objectReplicationTaskMapper.markRetrying(taskId,
                Math.max(0, retryCount),
                nextRetryAt,
                safeText(errorMessage, 500),
                LocalDateTime.now());
    }

    public void markDlq(Long taskId, int retryCount, String errorMessage) {
        if (taskId == null || taskId <= 0) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        objectReplicationTaskMapper.markDlq(taskId,
                Math.max(0, retryCount),
                safeText(errorMessage, 500),
                now,
                now);
    }

    public void recordAttemptLog(Long taskId,
                                 String traceId,
                                 int attemptNo,
                                 String status,
                                 Integer durationMs,
                                 String errorMessage) {
        if (taskId == null || taskId <= 0) {
            return;
        }
        objectReplicationTaskMapper.insertLog(taskId,
                safeText(traceId, 100),
                Math.max(1, attemptNo),
                safeText(status, 20),
                durationMs,
                safeText(errorMessage, 500),
                LocalDateTime.now());
    }

    private int normalizePriority(int priority) {
        if (priority < 0) {
            return 0;
        }
        return Math.min(priority, 9);
    }

    private void createOutboxEvent(Long taskId, int normalizedPriority, LocalDateTime now) {
        if (taskId == null || taskId <= 0) {
            return;
        }
        int queuePriority = toMqPriority(normalizedPriority);
        String payloadJson = "{\"taskId\":" + taskId + ",\"queuePriority\":" + queuePriority + "}";
        objectOutboxMapper.insertIgnore(
                OUTBOX_EVENT_TYPE_REPLICATION_PUBLISH,
                "object-replication-task:" + taskId,
                payloadJson,
                "pending",
                0,
                now,
                "",
                now,
                now
        );
    }

    private int toMqPriority(int normalizedPriority) {
        return Math.max(0, 9 - normalizePriority(normalizedPriority));
    }

    private SourcePayload fetchSourcePayload(String sourceUrl) throws IOException {
        if (sourceUrl == null || sourceUrl.isBlank()) {
            throw new IllegalArgumentException("sourceUrl 为空");
        }
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(sourceFetchTimeoutMs))
                .build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(sourceUrl))
                .timeout(Duration.ofMillis(sourceFetchTimeoutMs))
                .GET()
                .build();
        try {
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() >= 400) {
                throw new IOException("source fetch failed: http " + response.statusCode());
            }
            String contentType = response.headers().firstValue("Content-Type").orElse("application/octet-stream");
            return new SourcePayload(response.body() == null ? new byte[0] : response.body(), contentType);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("source fetch interrupted", ex);
        }
    }

    private int elapsedMs(long startedAt) {
        return (int) Math.max(0, System.currentTimeMillis() - startedAt);
    }

    private int parseInt(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
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

    public record ProcessResult(boolean success,
                                String targetUrl,
                                String errorMessage,
                                int durationMs,
                                int maxRetries) {
    }

    private record SourcePayload(byte[] content, String contentType) {
    }
}
