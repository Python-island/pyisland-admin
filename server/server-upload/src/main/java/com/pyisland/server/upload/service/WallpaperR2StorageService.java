package com.pyisland.server.upload.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.net.URI;
import java.util.UUID;

/**
 * 壁纸市场 Cloudflare R2 文件服务。
 */
@Service
public class WallpaperR2StorageService {

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

    /**
     * 上传壁纸市场文件到 R2，并返回公网 URL。
     * @param file 文件。
     * @param folder 目录。
     * @return 公网访问地址。
     * @throws IOException 上传异常。
     */
    public String upload(MultipartFile file, String folder) throws IOException {
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

        return buildPublicUrl(objectKey);
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
}
