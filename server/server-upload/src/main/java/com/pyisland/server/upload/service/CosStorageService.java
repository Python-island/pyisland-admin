package com.pyisland.server.upload.service;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.region.Region;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;
import java.util.UUID;

/**
 * 腾讯云 COS 文件服务。
 */
@Service
public class CosStorageService implements ObjectStorageClient {

    private static final String RESOURCE_AVATAR = "avatar";
    private static final String RESOURCE_WALLPAPER = "wallpaper";
    private static final String RESOURCE_FEEDBACK = "feedback";

    @Value("${tencent.cos.avatar.region:ap-guangzhou}")
    private String avatarRegion;

    @Value("${tencent.cos.avatar.secret-id:}")
    private String avatarSecretId;

    @Value("${tencent.cos.avatar.secret-key:}")
    private String avatarSecretKey;

    @Value("${tencent.cos.avatar.bucket-name:}")
    private String avatarBucketName;

    @Value("${tencent.cos.avatar.domain:}")
    private String avatarDomain;

    @Value("${tencent.cos.wallpaper.region:ap-guangzhou}")
    private String wallpaperRegion;

    @Value("${tencent.cos.wallpaper.secret-id:}")
    private String wallpaperSecretId;

    @Value("${tencent.cos.wallpaper.secret-key:}")
    private String wallpaperSecretKey;

    @Value("${tencent.cos.wallpaper.bucket-name:}")
    private String wallpaperBucketName;

    @Value("${tencent.cos.wallpaper.domain:}")
    private String wallpaperDomain;

    @Value("${tencent.cos.feedback.region:ap-guangzhou}")
    private String feedbackRegion;

    @Value("${tencent.cos.feedback.secret-id:}")
    private String feedbackSecretId;

    @Value("${tencent.cos.feedback.secret-key:}")
    private String feedbackSecretKey;

    @Value("${tencent.cos.feedback.bucket-name:}")
    private String feedbackBucketName;

    @Value("${tencent.cos.feedback.domain:}")
    private String feedbackDomain;

    @Override
    public StorageProvider provider() {
        return StorageProvider.COS;
    }

    /**
     * 上传文件到 COS 并返回可访问地址。
     * @param file 待上传文件。
     * @param folder COS 目录。
     * @return 文件公网地址。
     * @throws IOException 文件读取失败时抛出。
     */
    public String upload(MultipartFile file, String folder) throws IOException {
        return uploadObject(file, folder).publicUrl();
    }

    @Override
    public StorageUploadResult uploadObject(MultipartFile file, String folder) throws IOException {
        String safeFolder = normalizeFolder(folder);

        String originalFilename = file.getOriginalFilename();
        String ext = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            ext = originalFilename.substring(originalFilename.lastIndexOf('.'));
        }
        String objectKey = safeFolder + "/" + UUID.randomUUID() + ext;
        BucketConfig config = resolveBucketConfig(objectKey, "", "");
        validateConfig(config);

        COSCredentials credentials = new BasicCOSCredentials(config.secretId(), config.secretKey());
        ClientConfig clientConfig = new ClientConfig(new Region(config.region()));
        COSClient cosClient = new COSClient(credentials, clientConfig);
        try {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(file.getSize());
            if (file.getContentType() != null) {
                metadata.setContentType(file.getContentType());
            }
            PutObjectRequest request = new PutObjectRequest(config.bucketName(), objectKey, file.getInputStream(), metadata);
            cosClient.putObject(request);
        } finally {
            cosClient.shutdown();
        }

        String contentType = file.getContentType() == null ? "application/octet-stream" : file.getContentType();
        return new StorageUploadResult(
                provider(),
                config.bucketName(),
                objectKey,
                buildPublicUrl(config, objectKey),
                contentType,
                file.getSize()
        );
    }

    @Override
    public StorageUploadResult putObject(String objectKey, byte[] content, String contentType) throws IOException {
        return putObject(objectKey, content, contentType, "", "");
    }

    @Override
    public StorageUploadResult putObject(String objectKey,
                                         byte[] content,
                                         String contentType,
                                         String bizType,
                                         String fieldName) throws IOException {
        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalArgumentException("objectKey 不能为空");
        }
        BucketConfig config = resolveBucketConfig(objectKey, bizType, fieldName);
        validateConfig(config);

        COSCredentials credentials = new BasicCOSCredentials(config.secretId(), config.secretKey());
        ClientConfig clientConfig = new ClientConfig(new Region(config.region()));
        COSClient cosClient = new COSClient(credentials, clientConfig);
        try {
            byte[] safeContent = content == null ? new byte[0] : content;
            String safeContentType = contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType;
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(safeContent.length);
            metadata.setContentType(safeContentType);
            PutObjectRequest request = new PutObjectRequest(
                    config.bucketName(),
                    objectKey,
                    new java.io.ByteArrayInputStream(safeContent),
                    metadata
            );
            cosClient.putObject(request);
            return new StorageUploadResult(
                    provider(),
                    config.bucketName(),
                    objectKey,
                    buildPublicUrl(config, objectKey),
                    safeContentType,
                    safeContent.length
            );
        } finally {
            cosClient.shutdown();
        }
    }

    private String buildPublicUrl(BucketConfig config, String objectKey) {
        String safeKey = objectKey.startsWith("/") ? objectKey.substring(1) : objectKey;
        if (config.domain() != null && !config.domain().isBlank()) {
            String normalizedDomain = config.domain().startsWith("http") ? config.domain() : "https://" + config.domain();
            if (normalizedDomain.endsWith("/")) {
                return normalizedDomain + safeKey;
            }
            return normalizedDomain + "/" + safeKey;
        }
        return "https://" + config.bucketName() + ".cos." + config.region() + ".myqcloud.com/" + safeKey;
    }

    private BucketConfig resolveBucketConfig(String objectKey, String bizType, String fieldName) {
        String resourceType = resolveResourceType(objectKey, bizType, fieldName);
        if (RESOURCE_WALLPAPER.equals(resourceType)) {
            return new BucketConfig(
                    RESOURCE_WALLPAPER,
                    safeText(wallpaperRegion),
                    safeText(wallpaperSecretId),
                    safeText(wallpaperSecretKey),
                    safeText(wallpaperBucketName),
                    safeText(wallpaperDomain)
            );
        }
        if (RESOURCE_FEEDBACK.equals(resourceType)) {
            return new BucketConfig(
                    RESOURCE_FEEDBACK,
                    safeText(feedbackRegion),
                    safeText(feedbackSecretId),
                    safeText(feedbackSecretKey),
                    safeText(feedbackBucketName),
                    safeText(feedbackDomain)
            );
        }
        return new BucketConfig(
                RESOURCE_AVATAR,
                safeText(avatarRegion),
                safeText(avatarSecretId),
                safeText(avatarSecretKey),
                safeText(avatarBucketName),
                safeText(avatarDomain)
        );
    }

    private String resolveResourceType(String objectKey, String bizType, String fieldName) {
        String normalizedBizType = safeText(bizType).toLowerCase(Locale.ROOT);
        if (normalizedBizType.contains("wallpaper")) {
            return RESOURCE_WALLPAPER;
        }
        if (normalizedBizType.contains("feedback")) {
            return RESOURCE_FEEDBACK;
        }
        if (normalizedBizType.contains("avatar")) {
            return RESOURCE_AVATAR;
        }

        String normalizedFieldName = safeText(fieldName).toLowerCase(Locale.ROOT);
        if (normalizedFieldName.contains("thumb") || normalizedFieldName.contains("originalurl")) {
            return RESOURCE_WALLPAPER;
        }
        if (normalizedFieldName.contains("feedback")) {
            return RESOURCE_FEEDBACK;
        }
        if (normalizedFieldName.contains("avatar")) {
            return RESOURCE_AVATAR;
        }

        String normalizedKey = safeText(objectKey).toLowerCase(Locale.ROOT);
        if (normalizedKey.startsWith("wallpapers/")) {
            return RESOURCE_WALLPAPER;
        }
        if (normalizedKey.startsWith("feedback-")
                || normalizedKey.startsWith("feedback/")
                || normalizedKey.startsWith("issue-feedback/")) {
            return RESOURCE_FEEDBACK;
        }
        return RESOURCE_AVATAR;
    }

    private void validateConfig(BucketConfig config) {
        if (config.bucketName() == null || config.bucketName().isBlank()) {
            throw new IllegalStateException("腾讯云 COS bucket-name 未配置: " + config.resourceType());
        }
        if (config.secretId() == null || config.secretId().isBlank()
                || config.secretKey() == null || config.secretKey().isBlank()) {
            throw new IllegalStateException("腾讯云 COS 密钥未配置: " + config.resourceType());
        }
        if (config.region() == null || config.region().isBlank()) {
            throw new IllegalStateException("腾讯云 COS region 未配置: " + config.resourceType());
        }
    }

    private String normalizeFolder(String folder) {
        String normalized = safeText(folder);
        normalized = normalized.replace("\\", "/");
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized.isBlank() ? "misc" : normalized;
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    private record BucketConfig(String resourceType,
                                String region,
                                String secretId,
                                String secretKey,
                                String bucketName,
                                String domain) {
    }
}
