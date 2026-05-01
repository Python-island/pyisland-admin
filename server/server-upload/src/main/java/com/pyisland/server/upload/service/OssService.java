package com.pyisland.server.upload.service;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.ObjectMetadata;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;
import java.util.UUID;

/**
 * OSS 文件服务。
 */
@Service
public class OssService implements ObjectStorageClient {

    private static final String RESOURCE_ADMIN_AVATAR = "admin-avatar";
    private static final String RESOURCE_AVATAR = "avatar";
    private static final String RESOURCE_WALLPAPER = "wallpaper";
    private static final String RESOURCE_FEEDBACK = "feedback";
    private static final String RESOURCE_IDENTITY = "identity";

    @Value("${aliyun.oss.admin-avatar.endpoint:}")
    private String adminAvatarEndpoint;

    @Value("${aliyun.oss.admin-avatar.access-key-id:}")
    private String adminAvatarAccessKeyId;

    @Value("${aliyun.oss.admin-avatar.access-key-secret:}")
    private String adminAvatarAccessKeySecret;

    @Value("${aliyun.oss.admin-avatar.bucket-name:}")
    private String adminAvatarBucketName;

    @Value("${aliyun.oss.admin-avatar.domain:}")
    private String adminAvatarDomain;

    @Value("${aliyun.oss.avatar.endpoint:}")
    private String avatarEndpoint;

    @Value("${aliyun.oss.avatar.access-key-id:}")
    private String avatarAccessKeyId;

    @Value("${aliyun.oss.avatar.access-key-secret:}")
    private String avatarAccessKeySecret;

    @Value("${aliyun.oss.avatar.bucket-name:}")
    private String avatarBucketName;

    @Value("${aliyun.oss.avatar.domain:}")
    private String avatarDomain;

    @Value("${aliyun.oss.wallpaper.endpoint:}")
    private String wallpaperEndpoint;

    @Value("${aliyun.oss.wallpaper.access-key-id:}")
    private String wallpaperAccessKeyId;

    @Value("${aliyun.oss.wallpaper.access-key-secret:}")
    private String wallpaperAccessKeySecret;

    @Value("${aliyun.oss.wallpaper.bucket-name:}")
    private String wallpaperBucketName;

    @Value("${aliyun.oss.wallpaper.domain:}")
    private String wallpaperDomain;

    @Value("${aliyun.oss.feedback.endpoint:}")
    private String feedbackEndpoint;

    @Value("${aliyun.oss.feedback.access-key-id:}")
    private String feedbackAccessKeyId;

    @Value("${aliyun.oss.feedback.access-key-secret:}")
    private String feedbackAccessKeySecret;

    @Value("${aliyun.oss.feedback.bucket-name:}")
    private String feedbackBucketName;

    @Value("${aliyun.oss.feedback.domain:}")
    private String feedbackDomain;

    @Value("${aliyun.oss.identity.endpoint:}")
    private String identityEndpoint;

    @Value("${aliyun.oss.identity.access-key-id:}")
    private String identityAccessKeyId;

    @Value("${aliyun.oss.identity.access-key-secret:}")
    private String identityAccessKeySecret;

    @Value("${aliyun.oss.identity.bucket-name:}")
    private String identityBucketName;

    @Value("${aliyun.oss.identity.domain:}")
    private String identityDomain;

    @Override
    public StorageProvider provider() {
        return StorageProvider.OSS;
    }

    /**
     * 上传文件到 OSS 并返回可访问地址。
     * @param file 待上传文件。
     * @param folder OSS 目录。
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
            ext = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        String objectKey = safeFolder + "/" + UUID.randomUUID() + ext;
        BucketConfig config = resolveBucketConfig(objectKey, "", "");
        validateConfig(config);

        OSS ossClient = new OSSClientBuilder().build(normalizeEndpoint(config.endpoint()), config.accessKeyId(), config.accessKeySecret());
        try {
            ObjectMetadata metadata = new ObjectMetadata();
            String contentType = file.getContentType();
            metadata.setContentType(contentType);
            metadata.setContentLength(file.getSize());
            ossClient.putObject(config.bucketName(), objectKey, file.getInputStream(), metadata);
        } finally {
            ossClient.shutdown();
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
        OSS ossClient = new OSSClientBuilder().build(normalizeEndpoint(config.endpoint()), config.accessKeyId(), config.accessKeySecret());
        try {
            ObjectMetadata metadata = new ObjectMetadata();
            byte[] safeContent = content == null ? new byte[0] : content;
            String safeContentType = contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType;
            metadata.setContentType(safeContentType);
            metadata.setContentLength(safeContent.length);
            ossClient.putObject(config.bucketName(), objectKey, new java.io.ByteArrayInputStream(safeContent), metadata);
            return new StorageUploadResult(
                    provider(),
                    config.bucketName(),
                    objectKey,
                    buildPublicUrl(config, objectKey),
                    safeContentType,
                    safeContent.length
            );
        } finally {
            ossClient.shutdown();
        }
    }

    private String buildPublicUrl(BucketConfig config, String objectKey) {
        String safeKey = objectKey.startsWith("/") ? objectKey.substring(1) : objectKey;
        if (config.domain() != null && !config.domain().isBlank()) {
            String safeDomain = config.domain().startsWith("http") ? config.domain() : "https://" + config.domain();
            if (safeDomain.endsWith("/")) {
                return safeDomain + safeKey;
            }
            return safeDomain + "/" + safeKey;
        }
        String normalizedEndpoint = normalizeEndpoint(config.endpoint());
        String base = normalizedEndpoint.endsWith("/")
                ? normalizedEndpoint.substring(0, normalizedEndpoint.length() - 1)
                : normalizedEndpoint;
        return base + "/" + config.bucketName() + "/" + safeKey;
    }

    private BucketConfig resolveBucketConfig(String objectKey, String bizType, String fieldName) {
        String resourceType = resolveResourceType(objectKey, bizType, fieldName);
        if (RESOURCE_ADMIN_AVATAR.equals(resourceType)) {
            return new BucketConfig(
                    RESOURCE_ADMIN_AVATAR,
                    safeText(adminAvatarEndpoint),
                    safeText(adminAvatarAccessKeyId),
                    safeText(adminAvatarAccessKeySecret),
                    safeText(adminAvatarBucketName),
                    safeText(adminAvatarDomain)
            );
        }
        if (RESOURCE_WALLPAPER.equals(resourceType)) {
            return new BucketConfig(
                    RESOURCE_WALLPAPER,
                    safeText(wallpaperEndpoint),
                    safeText(wallpaperAccessKeyId),
                    safeText(wallpaperAccessKeySecret),
                    safeText(wallpaperBucketName),
                    safeText(wallpaperDomain)
            );
        }
        if (RESOURCE_FEEDBACK.equals(resourceType)) {
            return new BucketConfig(
                    RESOURCE_FEEDBACK,
                    safeText(feedbackEndpoint),
                    safeText(feedbackAccessKeyId),
                    safeText(feedbackAccessKeySecret),
                    safeText(feedbackBucketName),
                    safeText(feedbackDomain)
            );
        }
        if (RESOURCE_IDENTITY.equals(resourceType)) {
            return new BucketConfig(
                    RESOURCE_IDENTITY,
                    safeText(identityEndpoint),
                    safeText(identityAccessKeyId),
                    safeText(identityAccessKeySecret),
                    safeText(identityBucketName),
                    safeText(identityDomain)
            );
        }
        return new BucketConfig(
                RESOURCE_AVATAR,
                safeText(avatarEndpoint),
                safeText(avatarAccessKeyId),
                safeText(avatarAccessKeySecret),
                safeText(avatarBucketName),
                safeText(avatarDomain)
        );
    }

    private String resolveResourceType(String objectKey, String bizType, String fieldName) {
        String normalizedBizType = safeText(bizType).toLowerCase(Locale.ROOT);
        if (normalizedBizType.contains("admin_avatar")
                || normalizedBizType.contains("admin-avatar")
                || (normalizedBizType.contains("admin") && normalizedBizType.contains("avatar"))) {
            return RESOURCE_ADMIN_AVATAR;
        }
        if (normalizedBizType.contains("wallpaper")) {
            return RESOURCE_WALLPAPER;
        }
        if (normalizedBizType.contains("feedback")) {
            return RESOURCE_FEEDBACK;
        }
        if (normalizedBizType.contains("identity")) {
            return RESOURCE_IDENTITY;
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
        if (normalizedFieldName.contains("admin") && normalizedFieldName.contains("avatar")) {
            return RESOURCE_ADMIN_AVATAR;
        }
        if (normalizedFieldName.contains("avatar")) {
            return RESOURCE_AVATAR;
        }

        String normalizedKey = safeText(objectKey).toLowerCase(Locale.ROOT);
        if (normalizedKey.startsWith("admin-avatars/")
                || normalizedKey.startsWith("admin-avatar/")) {
            return RESOURCE_ADMIN_AVATAR;
        }
        if (normalizedKey.startsWith("wallpapers/")) {
            return RESOURCE_WALLPAPER;
        }
        if (normalizedKey.startsWith("feedback-")
                || normalizedKey.startsWith("feedback/")
                || normalizedKey.startsWith("issue-feedback/")) {
            return RESOURCE_FEEDBACK;
        }
        if (normalizedKey.startsWith("identity-material/")) {
            return RESOURCE_IDENTITY;
        }
        return RESOURCE_AVATAR;
    }

    private void validateConfig(BucketConfig config) {
        if (config.endpoint() == null || config.endpoint().isBlank()) {
            throw new IllegalStateException("阿里云 OSS endpoint 未配置: " + config.resourceType());
        }
        if (config.accessKeyId() == null || config.accessKeyId().isBlank()
                || config.accessKeySecret() == null || config.accessKeySecret().isBlank()) {
            throw new IllegalStateException("阿里云 OSS 密钥未配置: " + config.resourceType());
        }
        if (config.bucketName() == null || config.bucketName().isBlank()) {
            throw new IllegalStateException("阿里云 OSS bucket-name 未配置: " + config.resourceType());
        }
    }

    private String normalizeEndpoint(String endpoint) {
        String safeEndpoint = safeText(endpoint);
        if (safeEndpoint.startsWith("http://") || safeEndpoint.startsWith("https://")) {
            return safeEndpoint;
        }
        return "https://" + safeEndpoint;
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
                                String endpoint,
                                String accessKeyId,
                                String accessKeySecret,
                                String bucketName,
                                String domain) {
    }
}
