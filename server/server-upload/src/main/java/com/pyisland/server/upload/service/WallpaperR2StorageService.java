package com.pyisland.server.upload.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.UUID;

/**
 * 壁纸市场 Cloudflare R2 文件服务。
 */
@Service
public class WallpaperR2StorageService {

    private static final Logger log = LoggerFactory.getLogger(WallpaperR2StorageService.class);

    @Value("${cloudflare.wallpaper-r2.endpoint}")
    private String endpoint;

    @Value("${cloudflare.wallpaper-r2.access-key-id}")
    private String accessKeyId;

    @Value("${cloudflare.wallpaper-r2.access-key-secret}")
    private String accessKeySecret;

    @Value("${cloudflare.wallpaper-r2.bucket-name}")
    private String bucketName;

    @Value("${cloudflare.wallpaper-r2.public-domain:}")
    private String publicDomain;

    public StorageProvider provider() {
        return StorageProvider.R2;
    }

    /**
     * 上传壁纸市场文件到 R2，并返回公网 URL。
     * @param file 文件。
     * @param folder 目录。
     * @return 公网访问地址。
     * @throws IOException 上传异常。
     */
    public String upload(MultipartFile file, String folder) throws IOException {
        return uploadObject(file, folder).publicUrl();
    }

    public StorageUploadResult uploadObject(MultipartFile file, String folder) throws IOException {
        String originalFilename = file.getOriginalFilename();
        String ext = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            ext = originalFilename.substring(originalFilename.lastIndexOf('.'));
        }
        String objectKey = folder + "/" + UUID.randomUUID() + ext;

        AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKeyId, accessKeySecret);
        try (S3Client s3Client = S3Client.builder()
                .region(Region.of("auto"))
                .endpointOverride(URI.create(endpoint))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build()) {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .contentType(file.getContentType())
                    .build();
            s3Client.putObject(request, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
        }

        String contentType = file.getContentType() == null ? "application/octet-stream" : file.getContentType();
        return new StorageUploadResult(
                provider(),
                bucketName,
                objectKey,
                buildPublicUrl(objectKey),
                contentType,
                file.getSize()
        );
    }

    private String buildPublicUrl(String objectKey) {
        String safeKey = objectKey.startsWith("/") ? objectKey.substring(1) : objectKey;
        if (publicDomain != null && !publicDomain.isBlank()) {
            String domain = publicDomain.startsWith("http") ? publicDomain : "https://" + publicDomain;
            if (domain.endsWith("/")) {
                return domain + safeKey;
            }
            return domain + "/" + safeKey;
        }
        String base = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
        return base + "/" + bucketName + "/" + safeKey;
    }

    /**
     * 批量删除壁纸市场对象存储中的资源。
     * 支持传入公开域名 URL 或 endpoint/bucket 形式 URL；无法解析的 URL 会被忽略。
     * @param publicUrls 已入库的公网 URL 列表。
     */
    public void deleteAll(Collection<String> publicUrls) {
        if (publicUrls == null || publicUrls.isEmpty()) return;
        AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKeyId, accessKeySecret);
        try (S3Client s3Client = S3Client.builder()
                .region(Region.of("auto"))
                .endpointOverride(URI.create(endpoint))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build()) {
            for (String url : publicUrls) {
                String key = extractObjectKey(url);
                if (key == null || key.isBlank()) continue;
                try {
                    s3Client.deleteObject(DeleteObjectRequest.builder()
                            .bucket(bucketName)
                            .key(key)
                            .build());
                } catch (Exception ex) {
                    log.warn("Failed to delete wallpaper R2 object: key={}, reason={}", key, ex.getMessage());
                }
            }
        } catch (Exception ex) {
            log.warn("Failed to initialize S3 client for wallpaper R2 delete: {}", ex.getMessage());
        }
    }

    /**
     * 从已入库的公网 URL 反推 R2 对象 key。不匹配当前桶或域名时返回 null。
     */
    private String extractObjectKey(String url) {
        if (url == null || url.isBlank()) return null;
        String trimmed = url.trim();
        if (publicDomain != null && !publicDomain.isBlank()) {
            String domain = publicDomain.startsWith("http") ? publicDomain : "https://" + publicDomain;
            String prefix = domain.endsWith("/") ? domain : domain + "/";
            if (trimmed.startsWith(prefix)) {
                return trimmed.substring(prefix.length());
            }
        }
        if (endpoint != null && !endpoint.isBlank()) {
            String base = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
            String prefix = base + "/" + bucketName + "/";
            if (trimmed.startsWith(prefix)) {
                return trimmed.substring(prefix.length());
            }
        }
        return null;
    }
}
