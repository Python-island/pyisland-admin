package com.pyisland.server.upload.service;

import com.pyisland.server.upload.mapper.ObjectReplicationBackfillMapper;
import com.pyisland.server.upload.mapper.ObjectReplicationCheckpointMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 对象复制全量迁移与 DLQ 重放调度服务。
 */
@Service
public class ObjectReplicationBackfillService {

    private static final Logger log = LoggerFactory.getLogger(ObjectReplicationBackfillService.class);

    private static final String CHECKPOINT_USER_AVATAR = "backfill_user_avatar";
    private static final String CHECKPOINT_WALLPAPER_ASSET = "backfill_wallpaper_asset";
    private static final String CHECKPOINT_ISSUE_FEEDBACK = "backfill_issue_feedback";

    private final ObjectReplicationBackfillMapper objectReplicationBackfillMapper;
    private final ObjectReplicationCheckpointMapper objectReplicationCheckpointMapper;
    private final ObjectReplicationTaskService objectReplicationTaskService;
    private final boolean replicationEnabled;
    private final boolean backfillEnabled;
    private final int backfillBatchSize;
    private final boolean dlqReplayEnabled;
    private final int dlqReplayBatchSize;

    private final String r2Endpoint;
    private final String r2BucketName;
    private final String r2PublicDomain;
    private final String wallpaperR2Endpoint;
    private final String wallpaperR2BucketName;
    private final String wallpaperR2PublicDomain;
    private final String feedbackR2Endpoint;
    private final String feedbackR2BucketName;
    private final String feedbackR2PublicDomain;
    private final String ossAdminAvatarEndpoint;
    private final String ossAdminAvatarBucketName;
    private final String ossAdminAvatarDomain;
    private final String ossAvatarEndpoint;
    private final String ossAvatarBucketName;
    private final String ossAvatarDomain;
    private final String ossWallpaperEndpoint;
    private final String ossWallpaperBucketName;
    private final String ossWallpaperDomain;
    private final String ossFeedbackEndpoint;
    private final String ossFeedbackBucketName;
    private final String ossFeedbackDomain;
    private final String cosAvatarRegion;
    private final String cosAvatarBucketName;
    private final String cosAvatarDomain;
    private final String cosWallpaperRegion;
    private final String cosWallpaperBucketName;
    private final String cosWallpaperDomain;
    private final String cosFeedbackRegion;
    private final String cosFeedbackBucketName;
    private final String cosFeedbackDomain;

    private final AtomicBoolean backfillRunning = new AtomicBoolean(false);
    private final AtomicBoolean dlqReplayRunning = new AtomicBoolean(false);

    public ObjectReplicationBackfillService(ObjectReplicationBackfillMapper objectReplicationBackfillMapper,
                                            ObjectReplicationCheckpointMapper objectReplicationCheckpointMapper,
                                            ObjectReplicationTaskService objectReplicationTaskService,
                                            @Value("${object-replication.enabled:true}") boolean replicationEnabled,
                                            @Value("${object-replication.backfill-enabled:false}") boolean backfillEnabled,
                                            @Value("${object-replication.backfill-batch-size:200}") int backfillBatchSize,
                                            @Value("${object-replication.dlq-replay-enabled:false}") boolean dlqReplayEnabled,
                                            @Value("${object-replication.dlq-replay-batch-size:100}") int dlqReplayBatchSize,
                                            @Value("${cloudflare.r2.endpoint:}") String r2Endpoint,
                                            @Value("${cloudflare.r2.bucket-name:}") String r2BucketName,
                                            @Value("${cloudflare.r2.public-domain:}") String r2PublicDomain,
                                            @Value("${cloudflare.wallpaper-r2.endpoint:}") String wallpaperR2Endpoint,
                                            @Value("${cloudflare.wallpaper-r2.bucket-name:}") String wallpaperR2BucketName,
                                            @Value("${cloudflare.wallpaper-r2.public-domain:}") String wallpaperR2PublicDomain,
                                            @Value("${cloudflare.feedback-r2.endpoint:}") String feedbackR2Endpoint,
                                            @Value("${cloudflare.feedback-r2.bucket-name:}") String feedbackR2BucketName,
                                            @Value("${cloudflare.feedback-r2.public-domain:}") String feedbackR2PublicDomain,
                                            @Value("${aliyun.oss.admin-avatar.endpoint:}") String ossAdminAvatarEndpoint,
                                            @Value("${aliyun.oss.admin-avatar.bucket-name:}") String ossAdminAvatarBucketName,
                                            @Value("${aliyun.oss.admin-avatar.domain:}") String ossAdminAvatarDomain,
                                            @Value("${aliyun.oss.avatar.endpoint:}") String ossAvatarEndpoint,
                                            @Value("${aliyun.oss.avatar.bucket-name:}") String ossAvatarBucketName,
                                            @Value("${aliyun.oss.avatar.domain:}") String ossAvatarDomain,
                                            @Value("${aliyun.oss.wallpaper.endpoint:}") String ossWallpaperEndpoint,
                                            @Value("${aliyun.oss.wallpaper.bucket-name:}") String ossWallpaperBucketName,
                                            @Value("${aliyun.oss.wallpaper.domain:}") String ossWallpaperDomain,
                                            @Value("${aliyun.oss.feedback.endpoint:}") String ossFeedbackEndpoint,
                                            @Value("${aliyun.oss.feedback.bucket-name:}") String ossFeedbackBucketName,
                                            @Value("${aliyun.oss.feedback.domain:}") String ossFeedbackDomain,
                                            @Value("${tencent.cos.avatar.region:ap-guangzhou}") String cosAvatarRegion,
                                            @Value("${tencent.cos.avatar.bucket-name:}") String cosAvatarBucketName,
                                            @Value("${tencent.cos.avatar.domain:}") String cosAvatarDomain,
                                            @Value("${tencent.cos.wallpaper.region:ap-guangzhou}") String cosWallpaperRegion,
                                            @Value("${tencent.cos.wallpaper.bucket-name:}") String cosWallpaperBucketName,
                                            @Value("${tencent.cos.wallpaper.domain:}") String cosWallpaperDomain,
                                            @Value("${tencent.cos.feedback.region:ap-guangzhou}") String cosFeedbackRegion,
                                            @Value("${tencent.cos.feedback.bucket-name:}") String cosFeedbackBucketName,
                                            @Value("${tencent.cos.feedback.domain:}") String cosFeedbackDomain) {
        this.objectReplicationBackfillMapper = objectReplicationBackfillMapper;
        this.objectReplicationCheckpointMapper = objectReplicationCheckpointMapper;
        this.objectReplicationTaskService = objectReplicationTaskService;
        this.replicationEnabled = replicationEnabled;
        this.backfillEnabled = backfillEnabled;
        this.backfillBatchSize = Math.max(1, Math.min(backfillBatchSize, 1000));
        this.dlqReplayEnabled = dlqReplayEnabled;
        this.dlqReplayBatchSize = Math.max(1, Math.min(dlqReplayBatchSize, 500));
        this.r2Endpoint = safeText(r2Endpoint, 600);
        this.r2BucketName = safeText(r2BucketName, 120);
        this.r2PublicDomain = safeText(r2PublicDomain, 600);
        this.wallpaperR2Endpoint = safeText(wallpaperR2Endpoint, 600);
        this.wallpaperR2BucketName = safeText(wallpaperR2BucketName, 120);
        this.wallpaperR2PublicDomain = safeText(wallpaperR2PublicDomain, 600);
        this.feedbackR2Endpoint = safeText(feedbackR2Endpoint, 600);
        this.feedbackR2BucketName = safeText(feedbackR2BucketName, 120);
        this.feedbackR2PublicDomain = safeText(feedbackR2PublicDomain, 600);
        this.ossAdminAvatarEndpoint = safeText(ossAdminAvatarEndpoint, 300);
        this.ossAdminAvatarBucketName = safeText(ossAdminAvatarBucketName, 120);
        this.ossAdminAvatarDomain = safeText(ossAdminAvatarDomain, 600);
        this.ossAvatarEndpoint = safeText(ossAvatarEndpoint, 300);
        this.ossAvatarBucketName = safeText(ossAvatarBucketName, 120);
        this.ossAvatarDomain = safeText(ossAvatarDomain, 600);
        this.ossWallpaperEndpoint = safeText(ossWallpaperEndpoint, 300);
        this.ossWallpaperBucketName = safeText(ossWallpaperBucketName, 120);
        this.ossWallpaperDomain = safeText(ossWallpaperDomain, 600);
        this.ossFeedbackEndpoint = safeText(ossFeedbackEndpoint, 300);
        this.ossFeedbackBucketName = safeText(ossFeedbackBucketName, 120);
        this.ossFeedbackDomain = safeText(ossFeedbackDomain, 600);
        this.cosAvatarRegion = safeText(cosAvatarRegion, 80);
        this.cosAvatarBucketName = safeText(cosAvatarBucketName, 120);
        this.cosAvatarDomain = safeText(cosAvatarDomain, 600);
        this.cosWallpaperRegion = safeText(cosWallpaperRegion, 80);
        this.cosWallpaperBucketName = safeText(cosWallpaperBucketName, 120);
        this.cosWallpaperDomain = safeText(cosWallpaperDomain, 600);
        this.cosFeedbackRegion = safeText(cosFeedbackRegion, 80);
        this.cosFeedbackBucketName = safeText(cosFeedbackBucketName, 120);
        this.cosFeedbackDomain = safeText(cosFeedbackDomain, 600);
    }

    @Scheduled(fixedDelayString = "${object-replication.backfill-interval-ms:5000}")
    public void backfillLegacyResources() {
        if (!replicationEnabled || !backfillEnabled) {
            return;
        }
        if (!backfillRunning.compareAndSet(false, true)) {
            return;
        }
        try {
            processUserAvatarBatch();
            processWallpaperAssetBatch();
            processIssueFeedbackBatch();
        } finally {
            backfillRunning.set(false);
        }
    }

    @Scheduled(fixedDelayString = "${object-replication.dlq-replay-interval-ms:15000}")
    public void replayDlqTasks() {
        if (!replicationEnabled || !dlqReplayEnabled) {
            return;
        }
        if (!dlqReplayRunning.compareAndSet(false, true)) {
            return;
        }
        try {
            int replayed = objectReplicationTaskService.replayDlqTasks(dlqReplayBatchSize);
            if (replayed > 0) {
                log.info("object replication dlq replayed count={}", replayed);
            }
        } catch (Exception ex) {
            log.warn("object replication dlq replay failed reason={}", ex.getMessage());
        } finally {
            dlqReplayRunning.set(false);
        }
    }

    private void processUserAvatarBatch() {
        Map<String, Object> checkpoint = ensureCheckpoint(CHECKPOINT_USER_AVATAR);
        if (isDone(checkpoint)) {
            return;
        }

        long lastId = parseLong(checkpoint.get("lastId"), 0L);
        List<Map<String, Object>> rows = objectReplicationBackfillMapper.listUserAvatarRows(lastId, backfillBatchSize);
        if (rows == null || rows.isEmpty()) {
            markDone(CHECKPOINT_USER_AVATAR, lastId, "");
            return;
        }

        long maxId = lastId;
        int enqueued = 0;
        int skipped = 0;
        String lastError = "";
        for (Map<String, Object> row : rows) {
            long rowId = parseLong(row.get("id"), 0L);
            if (rowId <= 0) {
                continue;
            }
            maxId = Math.max(maxId, rowId);

            String avatarUrl = safeText(stringValue(row.get("avatar")), 2000);
            if (avatarUrl.isBlank()) {
                skipped++;
                continue;
            }
            StorageUploadResult source = resolveSourceUploadResult(avatarUrl);
            if (source == null) {
                skipped++;
                continue;
            }

            String role = safeText(stringValue(row.get("role")), 20).toLowerCase(Locale.ROOT);
            String bizType = "admin".equals(role) ? "admin_avatar" : "user_avatar";
            String username = safeText(stringValue(row.get("username")), 100);
            try {
                objectReplicationTaskService.enqueueForOtherProviders(
                        bizType,
                        rowId,
                        username,
                        "avatar",
                        source,
                        8
                );
                enqueued++;
            } catch (Exception ex) {
                lastError = safeText(ex.getMessage(), 500);
                skipped++;
                log.warn("object replication backfill enqueue failed scope={} id={} reason={}",
                        CHECKPOINT_USER_AVATAR,
                        rowId,
                        lastError);
            }
        }

        markRunning(CHECKPOINT_USER_AVATAR, maxId, lastError);
        log.info("object replication backfill progress scope={} scanned={} enqueued={} skipped={} lastId={}",
                CHECKPOINT_USER_AVATAR,
                rows.size(),
                enqueued,
                skipped,
                maxId);
    }

    private void processWallpaperAssetBatch() {
        Map<String, Object> checkpoint = ensureCheckpoint(CHECKPOINT_WALLPAPER_ASSET);
        if (isDone(checkpoint)) {
            return;
        }

        long lastId = parseLong(checkpoint.get("lastId"), 0L);
        List<Map<String, Object>> rows = objectReplicationBackfillMapper.listWallpaperAssetRows(lastId, backfillBatchSize);
        if (rows == null || rows.isEmpty()) {
            markDone(CHECKPOINT_WALLPAPER_ASSET, lastId, "");
            return;
        }

        long maxId = lastId;
        int enqueued = 0;
        int skipped = 0;
        String lastError = "";
        for (Map<String, Object> row : rows) {
            long rowId = parseLong(row.get("id"), 0L);
            if (rowId <= 0) {
                continue;
            }
            maxId = Math.max(maxId, rowId);

            String ownerUsername = safeText(stringValue(row.get("ownerUsername")), 100);
            enqueued += enqueueByUrl("wallpaper_asset", rowId, ownerUsername, "originalUrl", row.get("originalUrl"), 8);
            enqueued += enqueueByUrl("wallpaper_asset", rowId, ownerUsername, "thumb320Url", row.get("thumb320Url"), 8);
            enqueued += enqueueByUrl("wallpaper_asset", rowId, ownerUsername, "thumb720Url", row.get("thumb720Url"), 8);
            enqueued += enqueueByUrl("wallpaper_asset", rowId, ownerUsername, "thumb1280Url", row.get("thumb1280Url"), 8);

            if (safeText(stringValue(row.get("originalUrl")), 2000).isBlank()) {
                skipped++;
            }
            if (safeText(stringValue(row.get("thumb320Url")), 2000).isBlank()) {
                skipped++;
            }
            if (safeText(stringValue(row.get("thumb720Url")), 2000).isBlank()) {
                skipped++;
            }
            if (safeText(stringValue(row.get("thumb1280Url")), 2000).isBlank()) {
                skipped++;
            }
        }

        markRunning(CHECKPOINT_WALLPAPER_ASSET, maxId, lastError);
        log.info("object replication backfill progress scope={} scanned={} enqueued={} skipped={} lastId={}",
                CHECKPOINT_WALLPAPER_ASSET,
                rows.size(),
                enqueued,
                skipped,
                maxId);
    }

    private void processIssueFeedbackBatch() {
        Map<String, Object> checkpoint = ensureCheckpoint(CHECKPOINT_ISSUE_FEEDBACK);
        if (isDone(checkpoint)) {
            return;
        }

        long lastId = parseLong(checkpoint.get("lastId"), 0L);
        List<Map<String, Object>> rows = objectReplicationBackfillMapper.listIssueFeedbackRows(lastId, backfillBatchSize);
        if (rows == null || rows.isEmpty()) {
            markDone(CHECKPOINT_ISSUE_FEEDBACK, lastId, "");
            return;
        }

        long maxId = lastId;
        int enqueued = 0;
        int skipped = 0;
        for (Map<String, Object> row : rows) {
            long rowId = parseLong(row.get("id"), 0L);
            if (rowId <= 0) {
                continue;
            }
            maxId = Math.max(maxId, rowId);

            String username = safeText(stringValue(row.get("username")), 100);
            enqueued += enqueueByUrl("feedback_asset", rowId, username, "feedbackLogUrl", row.get("feedbackLogUrl"), 8);
            enqueued += enqueueByUrl("feedback_asset", rowId, username, "feedbackScreenshotUrl", row.get("feedbackScreenshotUrl"), 8);

            if (safeText(stringValue(row.get("feedbackLogUrl")), 2000).isBlank()) {
                skipped++;
            }
            if (safeText(stringValue(row.get("feedbackScreenshotUrl")), 2000).isBlank()) {
                skipped++;
            }
        }

        markRunning(CHECKPOINT_ISSUE_FEEDBACK, maxId, "");
        log.info("object replication backfill progress scope={} scanned={} enqueued={} skipped={} lastId={}",
                CHECKPOINT_ISSUE_FEEDBACK,
                rows.size(),
                enqueued,
                skipped,
                maxId);
    }

    private int enqueueByUrl(String bizType,
                             Long bizId,
                             String bizKey,
                             String fieldName,
                             Object rawUrl,
                             int priority) {
        String sourceUrl = safeText(stringValue(rawUrl), 2000);
        if (sourceUrl.isBlank()) {
            return 0;
        }
        StorageUploadResult source = resolveSourceUploadResult(sourceUrl);
        if (source == null) {
            return 0;
        }
        try {
            objectReplicationTaskService.enqueueForOtherProviders(
                    bizType,
                    bizId,
                    bizKey,
                    fieldName,
                    source,
                    priority
            );
            return 1;
        } catch (Exception ex) {
            log.warn("object replication backfill enqueue failed bizType={} bizId={} field={} reason={}",
                    bizType,
                    bizId,
                    fieldName,
                    ex.getMessage());
            return 0;
        }
    }

    private Map<String, Object> ensureCheckpoint(String checkpointKey) {
        LocalDateTime now = LocalDateTime.now();
        objectReplicationCheckpointMapper.insertIgnore(
                checkpointKey,
                0,
                "pending",
                "",
                null,
                now,
                now
        );
        Map<String, Object> checkpoint = objectReplicationCheckpointMapper.selectByKey(checkpointKey);
        if (checkpoint == null || checkpoint.isEmpty()) {
            return Map.of("lastId", 0L, "status", "pending");
        }
        return checkpoint;
    }

    private void markRunning(String checkpointKey, long lastId, String lastError) {
        objectReplicationCheckpointMapper.markRunning(
                checkpointKey,
                Math.max(0L, lastId),
                safeText(lastError, 500),
                LocalDateTime.now()
        );
    }

    private void markDone(String checkpointKey, long lastId, String lastError) {
        LocalDateTime now = LocalDateTime.now();
        objectReplicationCheckpointMapper.markDone(
                checkpointKey,
                Math.max(0L, lastId),
                safeText(lastError, 500),
                now,
                now
        );
        log.info("object replication backfill done scope={} lastId={}", checkpointKey, lastId);
    }

    private boolean isDone(Map<String, Object> checkpoint) {
        String status = safeText(stringValue(checkpoint.get("status")), 20).toLowerCase(Locale.ROOT);
        return "done".equals(status);
    }

    private StorageUploadResult resolveSourceUploadResult(String sourceUrl) {
        String normalizedUrl = safeText(sourceUrl, 2000);
        if (normalizedUrl.isBlank()) {
            return null;
        }

        StorageUploadResult result = resolveByDomain(normalizedUrl, StorageProvider.R2, r2BucketName, r2PublicDomain);
        if (result != null) {
            return result;
        }
        result = resolveByEndpointBucket(normalizedUrl, StorageProvider.R2, r2BucketName, r2Endpoint);
        if (result != null) {
            return result;
        }
        result = resolveByDomain(normalizedUrl, StorageProvider.R2, wallpaperR2BucketName, wallpaperR2PublicDomain);
        if (result != null) {
            return result;
        }
        result = resolveByEndpointBucket(normalizedUrl, StorageProvider.R2, wallpaperR2BucketName, wallpaperR2Endpoint);
        if (result != null) {
            return result;
        }
        result = resolveByDomain(normalizedUrl, StorageProvider.R2, feedbackR2BucketName, feedbackR2PublicDomain);
        if (result != null) {
            return result;
        }
        result = resolveByEndpointBucket(normalizedUrl, StorageProvider.R2, feedbackR2BucketName, feedbackR2Endpoint);
        if (result != null) {
            return result;
        }

        result = resolveByDomain(normalizedUrl, StorageProvider.OSS, ossAdminAvatarBucketName, ossAdminAvatarDomain);
        if (result != null) {
            return result;
        }
        result = resolveByVirtualHost(normalizedUrl, StorageProvider.OSS, ossAdminAvatarBucketName, ossAdminAvatarEndpoint);
        if (result != null) {
            return result;
        }
        result = resolveByDomain(normalizedUrl, StorageProvider.OSS, ossAvatarBucketName, ossAvatarDomain);
        if (result != null) {
            return result;
        }
        result = resolveByVirtualHost(normalizedUrl, StorageProvider.OSS, ossAvatarBucketName, ossAvatarEndpoint);
        if (result != null) {
            return result;
        }
        result = resolveByDomain(normalizedUrl, StorageProvider.OSS, ossWallpaperBucketName, ossWallpaperDomain);
        if (result != null) {
            return result;
        }
        result = resolveByVirtualHost(normalizedUrl, StorageProvider.OSS, ossWallpaperBucketName, ossWallpaperEndpoint);
        if (result != null) {
            return result;
        }
        result = resolveByDomain(normalizedUrl, StorageProvider.OSS, ossFeedbackBucketName, ossFeedbackDomain);
        if (result != null) {
            return result;
        }
        result = resolveByVirtualHost(normalizedUrl, StorageProvider.OSS, ossFeedbackBucketName, ossFeedbackEndpoint);
        if (result != null) {
            return result;
        }

        result = resolveByDomain(normalizedUrl, StorageProvider.COS, cosAvatarBucketName, cosAvatarDomain);
        if (result != null) {
            return result;
        }
        result = resolveByCosDefaultHost(normalizedUrl, cosAvatarBucketName, cosAvatarRegion);
        if (result != null) {
            return result;
        }
        result = resolveByDomain(normalizedUrl, StorageProvider.COS, cosWallpaperBucketName, cosWallpaperDomain);
        if (result != null) {
            return result;
        }
        result = resolveByCosDefaultHost(normalizedUrl, cosWallpaperBucketName, cosWallpaperRegion);
        if (result != null) {
            return result;
        }
        result = resolveByDomain(normalizedUrl, StorageProvider.COS, cosFeedbackBucketName, cosFeedbackDomain);
        if (result != null) {
            return result;
        }
        return resolveByCosDefaultHost(normalizedUrl, cosFeedbackBucketName, cosFeedbackRegion);
    }

    private StorageUploadResult resolveByDomain(String sourceUrl,
                                                StorageProvider provider,
                                                String bucketName,
                                                String domainOrUrl) {
        String objectKey = extractKeyByDomain(sourceUrl, domainOrUrl);
        if (objectKey == null || objectKey.isBlank()) {
            return null;
        }
        return buildSourceUploadResult(provider, bucketName, objectKey, sourceUrl);
    }

    private StorageUploadResult resolveByEndpointBucket(String sourceUrl,
                                                        StorageProvider provider,
                                                        String bucketName,
                                                        String endpoint) {
        String objectKey = extractKeyByEndpointBucket(sourceUrl, endpoint, bucketName);
        if (objectKey == null || objectKey.isBlank()) {
            return null;
        }
        return buildSourceUploadResult(provider, bucketName, objectKey, sourceUrl);
    }

    private StorageUploadResult resolveByVirtualHost(String sourceUrl,
                                                     StorageProvider provider,
                                                     String bucketName,
                                                     String endpoint) {
        String objectKey = extractKeyByVirtualHost(sourceUrl, bucketName, endpoint);
        if (objectKey == null || objectKey.isBlank()) {
            return null;
        }
        return buildSourceUploadResult(provider, bucketName, objectKey, sourceUrl);
    }

    private StorageUploadResult resolveByCosDefaultHost(String sourceUrl,
                                                        String bucketName,
                                                        String region) {
        String objectKey = extractKeyByCosDefaultHost(sourceUrl, bucketName, region);
        if (objectKey == null || objectKey.isBlank()) {
            return null;
        }
        return buildSourceUploadResult(StorageProvider.COS, bucketName, objectKey, sourceUrl);
    }

    private StorageUploadResult buildSourceUploadResult(StorageProvider provider,
                                                        String bucketName,
                                                        String objectKey,
                                                        String sourceUrl) {
        return new StorageUploadResult(
                provider,
                safeText(bucketName, 120),
                safeText(objectKey, 500),
                safeText(sourceUrl, 2000),
                "application/octet-stream",
                0
        );
    }

    private String extractKeyByDomain(String sourceUrl, String domainOrUrl) {
        URI sourceUri = toUri(sourceUrl);
        URI domainUri = toUri(domainOrUrl);
        if (sourceUri == null || domainUri == null) {
            return null;
        }
        if (!sameHost(sourceUri.getHost(), domainUri.getHost())) {
            return null;
        }
        String sourcePath = trimLeadingSlash(sourceUri.getRawPath());
        String domainPath = trimEdgeSlash(domainUri.getRawPath());
        if (!domainPath.isBlank()) {
            String prefix = domainPath.endsWith("/") ? domainPath : domainPath + "/";
            if (!sourcePath.startsWith(prefix)) {
                return null;
            }
            sourcePath = sourcePath.substring(prefix.length());
        }
        return sourcePath;
    }

    private String extractKeyByEndpointBucket(String sourceUrl, String endpoint, String bucketName) {
        if (bucketName == null || bucketName.isBlank()) {
            return null;
        }
        URI sourceUri = toUri(sourceUrl);
        URI endpointUri = toUri(endpoint);
        if (sourceUri == null || endpointUri == null) {
            return null;
        }
        if (!sameHost(sourceUri.getHost(), endpointUri.getHost())) {
            return null;
        }
        String sourcePath = trimLeadingSlash(sourceUri.getRawPath());
        String endpointPath = trimEdgeSlash(endpointUri.getRawPath());
        String bucketPart = trimEdgeSlash(bucketName);
        String prefix = endpointPath.isBlank()
                ? bucketPart + "/"
                : endpointPath + "/" + bucketPart + "/";
        if (!sourcePath.startsWith(prefix)) {
            return null;
        }
        return sourcePath.substring(prefix.length());
    }

    private String extractKeyByVirtualHost(String sourceUrl, String bucketName, String endpoint) {
        if (bucketName == null || bucketName.isBlank()) {
            return null;
        }
        URI sourceUri = toUri(sourceUrl);
        URI endpointUri = toUri(endpoint);
        if (sourceUri == null || endpointUri == null || sourceUri.getHost() == null || endpointUri.getHost() == null) {
            return null;
        }
        String expectedHost = bucketName.toLowerCase(Locale.ROOT) + "." + endpointUri.getHost().toLowerCase(Locale.ROOT);
        if (!expectedHost.equals(sourceUri.getHost().toLowerCase(Locale.ROOT))) {
            return null;
        }
        return trimLeadingSlash(sourceUri.getRawPath());
    }

    private String extractKeyByCosDefaultHost(String sourceUrl, String bucketName, String region) {
        if (bucketName == null || bucketName.isBlank() || region == null || region.isBlank()) {
            return null;
        }
        URI sourceUri = toUri(sourceUrl);
        if (sourceUri == null || sourceUri.getHost() == null) {
            return null;
        }
        String expectedHost = (bucketName + ".cos." + region + ".myqcloud.com").toLowerCase(Locale.ROOT);
        if (!expectedHost.equals(sourceUri.getHost().toLowerCase(Locale.ROOT))) {
            return null;
        }
        return trimLeadingSlash(sourceUri.getRawPath());
    }

    private URI toUri(String raw) {
        String text = safeText(raw, 2000);
        if (text.isBlank()) {
            return null;
        }
        try {
            if (text.startsWith("http://") || text.startsWith("https://")) {
                return URI.create(text);
            }
            return URI.create("https://" + text);
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean sameHost(String hostA, String hostB) {
        if (hostA == null || hostB == null) {
            return false;
        }
        return hostA.equalsIgnoreCase(hostB);
    }

    private String trimLeadingSlash(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        int index = 0;
        while (index < path.length() && path.charAt(index) == '/') {
            index++;
        }
        return index >= path.length() ? "" : path.substring(index);
    }

    private String trimEdgeSlash(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        int start = 0;
        int end = path.length();
        while (start < end && path.charAt(start) == '/') {
            start++;
        }
        while (end > start && path.charAt(end - 1) == '/') {
            end--;
        }
        if (start >= end) {
            return "";
        }
        return path.substring(start, end);
    }

    private long parseLong(Object value, long defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
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
}
