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

@Service
public class FeedbackR2StorageService {

    @Value("${cloudflare.feedback-r2.endpoint}")
    private String endpoint;

    @Value("${cloudflare.feedback-r2.access-key-id}")
    private String accessKeyId;

    @Value("${cloudflare.feedback-r2.access-key-secret}")
    private String accessKeySecret;

    @Value("${cloudflare.feedback-r2.bucket-name}")
    private String bucketName;

    @Value("${cloudflare.feedback-r2.public-domain:}")
    private String publicDomain;

    public StorageProvider provider() {
        return StorageProvider.R2;
    }

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
}
